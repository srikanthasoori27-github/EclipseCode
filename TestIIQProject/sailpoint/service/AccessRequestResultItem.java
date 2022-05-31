/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service;

import sailpoint.tools.Message;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Result object for returning data about a launched request
 *
 * @author: michael.hide
 * Created: 10/9/14 10:32 AM
 */
public class AccessRequestResultItem extends WorkflowResultItem {


    // approvalItems contains a list of maps where each map contains an approvalItemId/requestItemId pairing
    private List<Map<String, String>> approvalItems;
    private boolean allowViolations;
    private boolean requireViolationComment;

    /**
     * Constructor initializes member variables
     *
     * @param status    String workflow status
     * @param requestId String identity request id
     * @param workItemType String workflow workitem type
     * @param allowViolations Boolean indicating if this workitem allows violations to be submitted
     * @param workItemId String workflow workitem id
     * @param applItems  List<Map<String, String>> a list of maps where each map contains an approvalItemId/requestItemId pairing 
     * @param messages  List of localized String error messages
     */
    public AccessRequestResultItem(String status, String requestId, String workItemType, String workItemId,
                                   boolean allowViolations, boolean requireViolationComment,
                                   List<Map<String, String>> applItems, List<Message> messages) {
        super(status, requestId, workItemType, workItemId, messages);
        this.allowViolations = allowViolations;
        this.requireViolationComment = requireViolationComment;
        this.approvalItems = applItems == null ? Collections.<Map<String, String>>emptyList() : applItems;
    }

    /**
     * Gets a List of maps where each map contains an approvalItemId/requestItemId pairing
     *
     * @return  List<Map<String, String>> of List of maps where each map contains 
     * an approvalItemId/requestItemId pairing
     */
    public List<Map<String, String>> getApprovalItems() {
        return approvalItems;
    }

    /**
     * If violations should be allowed
     * @return True is should allow violations
     */
    public boolean isAllowViolations() {
        return allowViolations;
    }


    /**
     * Set if violations should be allowed
     * @param allowViolations if violations should be allowed
     */
    public void setAllowViolations(boolean allowViolations) {
        this.allowViolations = allowViolations;
    }

    /**
     * If submitting with violations requires a comment
     * @return True comment is required when submitting with violation
     */
    public boolean isRequireViolationComment() {
        return requireViolationComment;
    }

    /**
     * Set if submitting with violations requires a comment
     * @param requireViolationComment if comment is required when submitting with violation
     */
    public void setRequireViolationComment(boolean requireViolationComment) {
        this.requireViolationComment = requireViolationComment;
    }

}
