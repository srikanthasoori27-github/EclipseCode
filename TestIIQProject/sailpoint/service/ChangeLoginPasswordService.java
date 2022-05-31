package sailpoint.service;

import java.util.List;

import sailpoint.api.IdentityLifecycler;
import sailpoint.api.PasswordPolice;
import sailpoint.api.PasswordPolice.Expiry;
import sailpoint.api.SailPointContext;
import sailpoint.api.WorkflowSession;
import sailpoint.object.Capability;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.Source;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.identity.PasswordHelper;

/**
 * Service class for handling things related to changing an identity's login password
 */
public class ChangeLoginPasswordService {

    private final SailPointContext context;
    private final UserContext userContext;

    public ChangeLoginPasswordService(SailPointContext context, UserContext userContext) {
        this.context = context;
        this.userContext = userContext;
    }

    /**
     * Returns a localized list of password constraints
     * @return a localized list of password constraints
     */
    public List<String> getConstraints() throws GeneralException {
        PasswordPolice passwordPolice = new PasswordPolice(context);
        return passwordPolice.getIIQPasswordConstraints(userContext.getLocale(), userContext.getUserTimeZone(), false);
    }

    /**
     * Launches the update identity workflow to set/expire the password
     * @param requestee Who the password change is for
     * @param password The new password
     * @param resetRequired True if the password must be reset on next login
     * @return Result of Update Identity workflow
     */
    public WorkflowResultItem changePassword(Identity requestee, String password, boolean resetRequired) throws GeneralException {
        Identity requester = this.userContext.getLoggedInUser();
        /* Password can be null because this may just a request to expire the password*/
        if(!Util.isNullOrEmpty(password)) {
           /* Throws if constraints violated */
            validateNewPassword(requester, requestee, password);
        }
        /* When passwords are null or empty workflow doesn't trigger and no expiry is ste. Manually call PasswordPolice
         * to reset the password expiration. Would be nice to do this all in the workflow, but there are enough
         * places calling PasswordPolice that we can make it the single source for setting expirations. 
         */
        if (resetRequired) {
            PasswordPolice police = new PasswordPolice(this.context);
            police.setPasswordExpiration(requestee, Expiry.EXPIRE_NOW);
        }

        ProvisioningPlan plan = buildPlan(requester, requestee, password, resetRequired);
        // Launch the workflow and get the session
        IdentityLifecycler cycler = new IdentityLifecycler(context);
        WorkflowSession workflowSession = cycler.launchUpdate(null, requestee, plan);

        // Create the workflow result
        WorkflowSessionService workflowSessionService = new WorkflowSessionService(context, null, workflowSession);
        return workflowSessionService.createWorkflowResult(userContext, true);
    }

    /**
     * Builds a provisioning plan for the Identity Update workflow
     * @param requester The identity doing the requesting
     * @param requestee The identity being changed
     * @param password The new password
     * @param resetRequired If true expire the password
     * @return The provisioning plan
     */
    ProvisioningPlan buildPlan(Identity requester, Identity requestee, String password, boolean resetRequired) throws GeneralException {
        /* Create a new account request */
        ProvisioningPlan.AccountRequest accountRequest = new ProvisioningPlan.AccountRequest();
        accountRequest.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
        accountRequest.setApplication(ProvisioningPlan.APP_IIQ);

        Expiry expiry = (resetRequired) ?  Expiry.EXPIRE_NOW : Expiry.USE_RESET_EXPIRY;
        /* Add attributes to account request */
        PasswordHelper.addPasswordChangesToAccountRequest(requestee, password, accountRequest, expiry);

        /* Build the plan */
        ProvisioningPlan plan = new ProvisioningPlan();
        plan.setIdentity(requestee);
        plan.addRequester(requester);
        plan.setSource(Source.UI);
        plan.add(accountRequest);

        return plan;
    }

    /**
     * Call the password police to validate the password against the policy
     * @param requester The Identity doing the requesting
     * @param requestee The Identity whose password is being changed
     * @param password The new password
     * @throws GeneralException If the password policy is violated
     */
    private void validateNewPassword(Identity requester, Identity requestee, String password) throws GeneralException {
        PasswordPolice police = new PasswordPolice(this.context);
        police.checkPassword(requestee, password, Capability.hasSystemAdministrator(requester.getCapabilityManager().getEffectiveCapabilities()));
    }
}
