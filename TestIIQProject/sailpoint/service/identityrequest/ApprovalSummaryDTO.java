/*
 * (c) Copyright 2017. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.identityrequest;

import java.util.Date;
import java.util.List;

import sailpoint.object.Comment;
import sailpoint.object.WorkflowSummary;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Util;

/**
 * DTO object for WorkflowSummary.ApprovalSummary
 */
public class ApprovalSummaryDTO {
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    /**
     * Work item id for this approval summary
     */
    private String workItemId;

    /**
     * Display name of owner identity
     */
    private String ownerDisplayName;

    /**
     * List of comments
     */
    private List<Comment> comments;

    /**
     * Open date
     */
    private Date openDate;

    /**
     * End date
     */
    private Date completeDate;

    /**
     * Status (WorkItem.State message key)
     */
    private String status;

    /**
     * Count of approval items, if this is an approval.
     */
    private int approvalItemCount;

    /**
     * Description from the work item.
     */
    private String description;

    /**
     * WorkItemArchive id for this approval summary. This is set if the workItem cannot be found for the workItemId.
     */
    private String workItemArchiveId;

    /**
     * Boolean to indicate logged in user has access to the work item.
     */
    private boolean hasWorkItemAccess;

    /**
     * Name of the workItem.
     */
    private String workItemName;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////
    /**
     * Constructor
     */
    ApprovalSummaryDTO(WorkflowSummary.ApprovalSummary approvalSummary) throws InvalidParameterException {
        if (approvalSummary == null) {
            // can't create dto without object
            throw new InvalidParameterException("approvalSummary");
        }

        this.workItemId = approvalSummary.getWorkItemId();
        this.ownerDisplayName = approvalSummary.getOwner();
        // comments will be sanitized on front end
        this.comments = approvalSummary.getComments();
        this.openDate = approvalSummary.getStartDate();
        this.completeDate = approvalSummary.getEndDate();
        // default to open state
        this.status = approvalSummary.getStateKey();
        this.description = approvalSummary.getRequest();
        this.approvalItemCount = (approvalSummary.getApprovalSet() == null) ? 0 : Util.size(approvalSummary.getApprovalSet().getItems());
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Getters/Setters
    //
    //////////////////////////////////////////////////////////////////////
    public String getWorkItemId() {
        return workItemId;
    }

    public void setWorkItemId(String workItemId) {
        this.workItemId = workItemId;
    }

    public String getOwnerDisplayName() {
        return ownerDisplayName;
    }

    public void setOwnerDisplayName(String ownerDisplayName) {
        this.ownerDisplayName = ownerDisplayName;
    }

    public List<Comment> getComments() {
        return comments;
    }

    public void setComments(List<Comment> comments) {
        this.comments = comments;
    }

    public Date getOpenDate() {
        return openDate;
    }

    public void setOpenDate(Date openDate) {
        this.openDate = openDate;
    }


    public Date getCompleteDate() {
        return completeDate;
    }

    public void setCompleteDate(Date completeDate) {
        this.completeDate = completeDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getApprovalItemCount() {
        return approvalItemCount;
    }

    public void setApprovalItemCount(int approvalItemCount) {
        this.approvalItemCount = approvalItemCount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getWorkItemArchiveId() {
        return workItemArchiveId;
    }

    public void setWorkItemArchiveId(String workItemArchiveId) {
        this.workItemArchiveId = workItemArchiveId;
    }

    public boolean isHasWorkItemAccess() {
        return hasWorkItemAccess;
    }

    public void setHasWorkItemAccess(boolean hasWorkItemAccess) {
        this.hasWorkItemAccess = hasWorkItemAccess;
    }

    public String getWorkItemName() {
        return workItemName;
    }

    public void setWorkItemName(String workItemName) {
        this.workItemName = workItemName;
    }
}
