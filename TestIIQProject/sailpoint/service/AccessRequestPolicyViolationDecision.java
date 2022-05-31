/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service;

import java.util.ArrayList;
import java.util.Map;

import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Util;
import sailpoint.web.workitem.ApprovalItemGridHelper;
import sailpoint.web.workitem.ViolationReviewWorkItemDTO;

/**
 * Help DTO for transferring data about policy violation remediations to service classes.
 *
 * Created: 4/24/15 3:30 PM
 */
public class AccessRequestPolicyViolationDecision {

    /**
     * Private variables to hold the data
     */
    private WorkItem _workItem;
    private ArrayList<String> rejectedApprovalItemIds;
    private String violationDecision;
    private String completionComment;

    /**
     * Constant used in the JSON for patching a policy violation.
     * Value will be either 'remediate' or 'ignore'.
     */
    public static final String VIOLATION_REVIEW_DECISION = "violationReviewDecision";
    /**
     * Constant used in the JSON for patching a policy violation.
     * List of ApprovalItem ids to set the state to WorkItem.State.Rejected.
     */
    public static final String REJECTED_APPROVAL_ITEMS = "rejectedApprovalItems";
    /**
     * Values passed to VIOLATION_REVIEW_DECISION
     */
    public static final String REMEDIATE = "remediate";
    public static final String IGNORE = "ignore";


    /**
     * Constructor takes a WorkItem and a Map containing the decision.
     *
     * @param workItem
     * @param values
     */
    public AccessRequestPolicyViolationDecision(WorkItem workItem, Map<String, Object> values) throws GeneralException {
        this._workItem = workItem;
        if (this._workItem == null) {
            throw new InvalidParameterException("WorkItem required in constructor.");
        }
        if (values != null) {
            String decision = (String) values.get(VIOLATION_REVIEW_DECISION);
            if (workItem.getBoolean(ViolationReviewWorkItemDTO.REQUIRE_VIOLATION_COMMENT)) {
                completionComment = (String) values.get(ApprovalItemGridHelper.JSON_COMPLETION_COMMENTS);
            }
            if (Util.isNotNullOrEmpty(decision) && (decision.equals(REMEDIATE) || decision.equals(IGNORE))) {
                if (decision.equals(IGNORE) && !workItem.getAttributes().getBoolean("allowRequestsWithViolations")) {
                    throw new GeneralException("Violation decision of type ignore is not supported by the workitem");
                }
                this.violationDecision = decision;
            }
            else {
                throw new GeneralException("Violation decision is null or is not correct type: " + decision);
            }

            if (!Util.isEmpty((ArrayList<String>) values.get(REJECTED_APPROVAL_ITEMS))) {
                rejectedApprovalItemIds = (ArrayList<String>) values.get(REJECTED_APPROVAL_ITEMS);
            }
        }
        else {
            throw new InvalidParameterException("Value map not found.");
        }
    }

    /**
     * Accessor method for WorkItem
     *
     * @return the WorkItem
     */
    public WorkItem getWorkItem() {
        return this._workItem;
    }

    /**
     * Filters the violation decision to be either 'remediate' or 'ignore'.
     *
     * @return the violation decision
     */
    public String getViolationReviewDecision() {
        return this.violationDecision;
    }

    /**
     * Returns the list of rejected approval item ids
     *
     * @return ArrayList of ApprovalItem ids.
     */
    public ArrayList<String> getRejectedApprovalItemIds() {
        return this.rejectedApprovalItemIds;
    }

    /**
     * Return comment
     * 
     * @return Comment completionComment
     */
    public String getCompletionComment() {
        return completionComment;
    }
}
