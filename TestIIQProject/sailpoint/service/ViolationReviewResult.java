/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service;

import java.util.List;

import sailpoint.object.WorkItem;
import sailpoint.web.workitem.WorkItemDTO;


/**
 * ViolationReviewResult holds resulting information from attempting to resolve a violation review
 * that occurred during an access request.
 */
public class ViolationReviewResult extends WorkItemResult {

    private WorkItemDTO nextWorkItem;
    private String identityRequestId;
    private String workflowStatus;


    /**
     * Constructor.
     *
     * @param workItem  The possibly-null next work item that was generated in the workflow.
     * @param nextWorkItem  A possibly-null DTO of the next work item generated in the workflow.
     * @param requestId  The IdentityRequest ID.
     * @param workflowStatus  One of the WorkflowLaunch.STATUS_* constants.
     * @param errors  A list of translated errors that occurred in the workflow.
     */
    public ViolationReviewResult(WorkItem workItem, WorkItemDTO nextWorkItem, String requestId,
                                 String workflowStatus, List<MessageDTO> errors) {
        super(workItem);

        this.nextWorkItem = nextWorkItem;
        this.identityRequestId = requestId;
        this.workflowStatus = workflowStatus;
        this.addMessages(errors);
    }

    /**
     * Copy constructor.
     *
     * @param result  The result to copy from.
     */
    public ViolationReviewResult(WorkItemResult result) {
        super(result);

        if (result instanceof ViolationReviewResult) {
            ViolationReviewResult vr = (ViolationReviewResult) result;
            this.nextWorkItem = vr.nextWorkItem;
            this.identityRequestId = vr.identityRequestId;
            this.workflowStatus = vr.workflowStatus;
        }
    }

    public WorkItemDTO getNextWorkItem() {
        return nextWorkItem;
    }

    public String getIdentityRequestId() {
        return identityRequestId;
    }

    public String getWorkflowStatus() {
        return workflowStatus;
    }
}
