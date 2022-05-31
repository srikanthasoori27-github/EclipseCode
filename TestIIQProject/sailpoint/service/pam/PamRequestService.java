/* (c) Copyright 2017. SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.pam;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalSet;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.service.pam.PamRequest.PamAccountRequest;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * This service contains methods that can deal with PAM request information.
 */
public class PamRequestService {

    private SailPointContext context;


    /**
     * Constructor.
     *
     * @param context  The SailPointContext.
     */
    public PamRequestService(SailPointContext context) {
        this.context = context;
    }

    /**
     * Create a PamRequest using given arguments.
     *
     * @param plan  The ProvisioningPlan with the PAM request to add/remove an identity on a container.
     * @param identityName  The name of the identity being added/removed.
     * @param identityDisplayName  The display name of the identity being added/removed.
     * @param containerName  The name of the container the identity is being added/removed on.
     * @param containerDisplayName  The display name of the container the identity is being added/removed on.
     *
     * @return A PamRequest.
     */
    public PamRequest createPamRequest(ProvisioningPlan plan, String identityName, String identityDisplayName,
                                       String containerName, String containerDisplayName, String containerOwnerName)
        throws GeneralException {

        PamRequest req = new PamRequest(identityName, identityDisplayName, containerName, containerDisplayName, containerOwnerName);

        for (AccountRequest acctReq : Util.iterate(plan.getAccountRequests())) {
            PamAccountRequest pamAcctReq =
                new PamAccountRequest(acctReq.getApplication(), acctReq.getNativeIdentity(), getLinkDisplayName(identityName, acctReq));
            req.addAccountRequest(pamAcctReq);

            for (PermissionRequest permReq : Util.iterate(acctReq.getPermissionRequests())) {
                pamAcctReq.addRights(permReq);
            }
        }

        return req;
    }

    /**
     * Return the display name of the Link on the given identity for the requested account.
     *
     * @param identity  The name of the identity.
     * @param acctReq  The AccountRequest with the link information.
     *
     * @return The display name of the Link on the given identity for the requested account.
     */
    private String getLinkDisplayName(String identity, AccountRequest acctReq) throws GeneralException {
        return ObjectUtil.getAccountId(this.context, identity, acctReq.getApplication(), acctReq.getInstance(), acctReq.getNativeIdentity());
    }

    /**
     * Create an ApprovalSet from the given PamRequest.
     *
     * @param request  The PamRequest.
     *
     * @return An ApprovalSet with information from the given PamRequest.
     */
    public ApprovalSet createApprovalSet(PamRequest request) {
        ApprovalSet set = new ApprovalSet();

        for (PamAccountRequest acctReq : Util.iterate(request.getAccountRequests())) {
            for (String right : Util.iterate(acctReq.getAddedRights())) {
                set.add(createApprovalItem(request, acctReq, right, true));
            }
            for (String right : Util.iterate(acctReq.getRemovedRights())) {
                set.add(createApprovalItem(request, acctReq, right, false));
            }
        }

        return set;
    }

    /**
     * Create an ApprovalItem using the information from the given PamRequest and PamAccountRequest for the given right.
     *
     * @param request  The PamRequest with the approval information.
     * @param acctReq  The PamAccountRequest with information about the account that is being modified.
     * @param right  The right that is being added or removed.
     * @param isAdd  True if the right is being added to the account, false if it is being removed.
     *
     * @return An ApprovalItem with the information from the given requests and right.
     */
    private ApprovalItem createApprovalItem(PamRequest request, PamAccountRequest acctReq, String right, boolean isAdd) {
        ApprovalItem item = new ApprovalItem();

        ProvisioningPlan.Operation op = (isAdd) ? ProvisioningPlan.Operation.Add : ProvisioningPlan.Operation.Remove;

        item.setApplication(acctReq.getApplication());
        item.setNativeIdentity(acctReq.getNativeIdentity());
        item.setOperation(op.name());
        item.setName(request.getContainerName());
        item.setDisplayName(request.getContainerDisplayName());
        item.setValue(right);
        item.setDisplayValue(right);

        item.setState(request.getApprovalState());
        item.setApprover(request.getApprover());
        item.setOwner(request.getApprovalOwner());

        return item;
    }
}
