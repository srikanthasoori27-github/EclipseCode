/* (c) Copyright 2017. SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.workflow;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.ApprovalSet;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.Workflow;
import sailpoint.object.Workflow.Approval;
import sailpoint.service.pam.PamRequest;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * The PAMApprovalGenerator extends the IdentityApprovalGenerator to create approvals for PAM requests based
 * on the approvalScheme and other workflow arguments.
 */
public class PAMApprovalGenerator extends IdentityApprovalGenerator {
    private static Log log = LogFactory.getLog(PAMApprovalGenerator.class);

    /**
     * The name of the workflow argument that has the ProvisioningPlan for the PAM request.
     */
    public static final String ARG_PLAN = "plan";

    /**
     * The name of the workflow argument that has the pamRequest
     */
    public static final String ARG_PAM_REQUEST = "pamRequest";

    /**
     * The name of the workflow argument that has the container name.
     */
    public static final String ARG_CONTAINER_NAME = "containerName";

    /**
     * The name of the workflow argument that has the container display name.
     */
    public static final String ARG_CONTAINER_DISPLAY_NAME = "containerDisplayName";

    /**
     * The name of the workflow argument that has the container owner name.
     */
    public static final String ARG_CONTAINER_OWNER_NAME = "containerOwnerName";


    private ProvisioningPlan plan;
    private String containerName;
    private String containerDisplayName;
    private String containerOwnerName;
    private PamRequest pamRequest;


    /**
     * Constructor.
     *
     * @param wfc  The WorkflowContext.
     */
    public PAMApprovalGenerator(WorkflowContext wfc) {
        super(wfc);
    }

    /**
     * Initialize the generator by pulling workflow arguments into variables.
     */
    @Override
    protected void init() throws GeneralException {
        super.init();

        this.plan = (ProvisioningPlan) _args.get(ARG_PLAN);
        if (null == this.plan) {
            throw new GeneralException("plan is required");
        }

        this.containerName = (String) _args.get(ARG_CONTAINER_NAME);
        if (null == this.containerName) {
            throw new GeneralException("containerName is required");
        }

        // Not required - fall back to containerName if not available.
        this.containerDisplayName = (String) _args.get(ARG_CONTAINER_DISPLAY_NAME);

        this.containerOwnerName = (String) _args.get(ARG_CONTAINER_OWNER_NAME);

        this.pamRequest = (PamRequest) _args.get(ARG_PAM_REQUEST);
        if (null == this.pamRequest) {
            throw new GeneralException("pamRequest is required");
        }
    }

    /**
     * PAM appprovals do not use an ApprovalSet.
     *
     * @return False.
     */
    protected boolean isApprovalSetRequired() {
        return false;
    }

    /**
     * Create a PAM approval for the given approver.
     *
     * @param set  This is null since PAM approvals do not use an ApprovalSet.
     * @param approverName  The name of the approver.
     *
     * @return The Approval.
     */
    @Override
    protected Approval buildApprovalInternal(ApprovalSet set, String approverName) throws GeneralException {
        Approval approval = new Approval();

        // quote the owner in case the user name has a comma
        if ( approverName != null ) {
            approval.setOwner("\"" + approverName + "\"");
        }

        approval.put(ARG_PLAN, this.plan);
        approval.addArg(Workflow.ARG_WORK_ITEM_TARGET_CLASS, "sailpoint.object.Identity");
        approval.addArg(Workflow.ARG_WORK_ITEM_TARGET_NAME, _identityName);

        // If the owner is the requester and there is no esig, auto-complete the work item.
        approval.setComplete(isAutoApprove(approverName));

        return approval;
    }

    @Override
    protected List<Approval> getOwnerApprovalsInternal() throws GeneralException {
        Approval approval = new Approval();
        String approver = "";
        if (Util.isNotNullOrEmpty(this.containerOwnerName)) {
            approval.setOwner("\"" + this.containerOwnerName + "\"");
            approver = this.containerOwnerName;
        } else {
            // There was no container owner so try finding the PAM app owner
            if (!Util.isEmpty(pamRequest.getAccountRequests())) {
                // PAM requests are limited to 1 container at a time which means every account request would have
                // the same application
                String applicationName = pamRequest.getAccountRequests().get(0).getApplication();
                if (Util.isNotNullOrEmpty(applicationName)) {
                    Application application = _context.getObjectByName(Application.class, applicationName);
                    if ( application == null ) {
                        throw new GeneralException("Couldn't find application [" + applicationName + "]");
                    }

                    Identity owner = application.getOwner();
                    if (owner != null) {
                        String ownerName = owner.getName();
                        if (log.isDebugEnabled()) {
                            log.debug("Container Owner could not be resolved. Using Container Application owner '" + ownerName + "'.");
                        }
                        approval.setOwner("\"" + ownerName + "\"");
                        approver = ownerName;
                    } else {
                        if ( log.isDebugEnabled() ) {
                            log.debug("Container Owner and Application Owner could not be resolved. Using fallbackApprover '"+_fallBackApprover+"'.");
                        }
                        approval.setOwner("\"" + this._fallBackApprover + "\"");
                        approver = this._fallBackApprover;
                    }
                }
            }
        }

        approval.put(ARG_PLAN, this.plan);
        approval.addArg(Workflow.ARG_WORK_ITEM_TARGET_CLASS, "sailpoint.object.Identity");
        approval.addArg(Workflow.ARG_WORK_ITEM_TARGET_NAME, _identityName);

        // If the owner is the requester and there is no esig, auto-complete the work item.
        approval.setComplete(isAutoApprove(approver));

        return Arrays.asList(approval);
    }

    /**
     * Create the description for the approval work item.
     *
     * @param type  The type of approval.
     * @param approval  The Approval object.
     *
     * @return The description for the approval work item.
     */
    @Override
    protected String createApprovalDescription(String type, Approval approval) {
        String container = (null != this.containerDisplayName) ? this.containerDisplayName : this.containerName;
        return Util.splitCamelCase(type) + " Approval - Access Changes for " + _identityDisplayName + " on '" + container + "'";
    }
}
