/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service;

import sailpoint.api.Differencer;
import sailpoint.api.IdentityLifecycler;
import sailpoint.api.SailPointContext;
import sailpoint.api.WorkflowSession;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.Source;
import sailpoint.service.identity.ForwardingInfoDTO;
import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;
import sailpoint.web.identity.ForwardingHelper;

/**
 * Provides functionality for forwarding requests for identities.  Returns the current ForwardingInfo for an identity
 * as well as updates the ForwardingInfo for a given identity through a workflow
 */
public class ForwardingService {

    private Identity requester;
    private Identity identity;
    private SailPointContext context;
    private UserContext userContext;

    public ForwardingService(Identity requester, Identity identity, SailPointContext context, UserContext userContext) {
        this.requester = requester;
        this.identity = identity;
        this.context = context;
        this.userContext = userContext;
    }

    /**
     * Return a ForwardingInfoDTO object from the given identity
     * @return ForwardingInfoDTO the forwarding info object that contains the forwarding info
     * @throws GeneralException
     */
    public ForwardingInfoDTO getForwardInfo() throws GeneralException {
        return new ForwardingInfoDTO(identity, context);
    }

    /**
     * Update the forwarding info on the current identity
     * @param forwardingInfo {ForwardingInfoDTO} the forwarding info object that contains the forwarding info
     * @return WorkflowResultItem The result of the workflow
     * @throws GeneralException
     */
    public WorkflowResultItem updateForwardInfo(ForwardingInfoDTO forwardingInfo) throws GeneralException {
        ForwardingInfoDTO existingForwardingInfo = new ForwardingInfoDTO(identity, context);
        if(existingForwardingInfo.getForwardUser()==null || !Differencer.objectsEqual(existingForwardingInfo, forwardingInfo)) {
            // Build the provisioning plan
            ProvisioningPlan.AccountRequest accountRequest = this.createAccountRequest(forwardingInfo);
            ProvisioningPlan plan = this.createPlan(accountRequest);

            // Launch the workflow and get the session
            IdentityLifecycler cycler = new IdentityLifecycler(context);
            WorkflowSession workflowSession = cycler.launchUpdate(null, this.identity, plan);

            // Create the workflow result
            WorkflowSessionService workflowSessionService = new WorkflowSessionService(context, null, workflowSession);
            return workflowSessionService.createWorkflowResult(this.userContext, true);
        }

        return null;
    }

    /**
     * Creates an AccountRequest for the forwarding user change
     * @param forwardingInfo the forwarding info object that contains the forwarding info
     * @return The account request created out of the forwarding info
     * @throws GeneralException
     */
    public ProvisioningPlan.AccountRequest createAccountRequest(ForwardingInfoDTO forwardingInfo) throws GeneralException {
        ProvisioningPlan.AccountRequest accountRequest = new ProvisioningPlan.AccountRequest();
        accountRequest.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
        accountRequest.setApplication(ProvisioningPlan.APP_IIQ);

        String newForwardName = forwardingInfo.getForwardUser() != null ? forwardingInfo.getForwardUser().getName() : null;
        ForwardingHelper.addForwardingInfoToRequest(this.identity, newForwardName, forwardingInfo.getStartDate(), forwardingInfo.getEndDate(), accountRequest);

        return accountRequest;
    }

    /**
     * Build the provisioning plan with the current identity and requestor
     * @param accountRequest The account request for the forwarding change
     * @return The provisioning plan
     */
    ProvisioningPlan createPlan(ProvisioningPlan.AccountRequest accountRequest) {
        ProvisioningPlan plan = new ProvisioningPlan();
        plan.setIdentity(identity);

        // metadata for assignments and logging
        Identity requester = this.requester;
        plan.addRequester(requester);
        plan.setSource(Source.UI);
        plan.add(accountRequest);

        return plan;
    }
}