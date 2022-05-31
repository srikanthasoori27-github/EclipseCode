/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.identity;


import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequestItem;
import sailpoint.service.BaseDTO;
import sailpoint.tools.Util;

import java.util.Date;

/**
 * IdentityEntitlement object contain references to IdentityRequestItem objects so that they can reference
 * current and past request items that have affected the entitlement.  This DTO is a representation
 * of those identity request items
 */
public class IdentityEntitlementIdentityRequestItemDTO extends BaseDTO {

    /**
     * The name/requestId of the IdentityRequestItem
     */
    private String name;

    /**
     * The requestId of the IdentityRequestItem with the leading zeros removed
     */
    private String requestId;

    /**
     * The name/requestId of the IdentityRequestItem with the leading zeros removed
     */
    private String trimmedRequestId;

    /**
     * The operation of the request item such as 'Modify' or 'Add'
     */
    private String operation;

    /**
     * How the identity request was created such as from the UI
     */
    private String source;

    /**
     * The date of the request
     */
    private Date date;

    /**
     * The display name of the requester
     */
    private String requester;

    /**
     * The status of where this item is in the execution of the workflow such as 'pending approval'
     */
    private IdentityRequest.ExecutionStatus executionStatus;

    /**
     * Constructor
     *
     * @param item The IdentityRequestItem object we are using as the source
     */
    public IdentityEntitlementIdentityRequestItemDTO(IdentityRequestItem item) {
        this.name = item.getName();
        this.requestId = item.getIdentityRequest().getName();
        if (requestId != null) {
            trimmedRequestId = Util.stripLeadingChar(requestId, '0');
        }
        this.operation = item.getOperation();
        this.requester = item.getIdentityRequest().getRequesterDisplayName();
        this.date = item.getIdentityRequest().getCreated();
        this.source = item.getIdentityRequest().getSource();
        this.executionStatus = item.getIdentityRequest().getExecutionStatus();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getTrimmedRequestId() {
        return trimmedRequestId;
    }

    public void setTrimmedRequestId(String trimmedRequestId) {
        this.trimmedRequestId = trimmedRequestId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getRequester() {
        return requester;
    }

    public void setRequester(String requester) {
        this.requester = requester;
    }

    public IdentityRequest.ExecutionStatus getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(IdentityRequest.ExecutionStatus executionStatus) {
        this.executionStatus = executionStatus;
    }
}
