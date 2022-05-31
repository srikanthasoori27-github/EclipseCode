/*
 * (c) Copyright 2017. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.identityrequest;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import sailpoint.object.IdentityRequest;
import sailpoint.object.WorkflowSummary.ApprovalSummary;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Util;

/**
 * Identity request DTO
 */
public class IdentityRequestDTO {

    /**
     * Inner utility class to represent the policy violation data
     */
    class PolicyViolation {
        /**
         * policy name
         */
        private String policyName;

        /**
         * policy type
         */
        private String policyType;

        /**
         * rule name
         */
        private String ruleName;

        /**
         * Constructor
         * @param policyName name of policy
         * @param policyType type of policy
         * @param ruleName name of policy rule
         */
        PolicyViolation(String policyName, String policyType, String ruleName) {
            this.policyName = policyName;
            this.policyType = policyType;
            this.ruleName = ruleName;
        }

        public String getPolicyName() {
            return policyName;
        }

        public void setPolicyName(String policyName) {
            this.policyName = policyName;
        }

        public String getPolicyType() {
            return policyType;
        }

        public void setPolicyType(String policyType) {
            this.policyType = policyType;
        }

        public String getRuleName() {
            return ruleName;
        }

        public void setRuleName(String ruleName) {
            this.ruleName = ruleName;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * request type. store as string so we don't need to maintain multiple master request type lists.
     */
    private String type;

    /**
     * target display name
     */
    private String targetDisplayName;

    /**
     * requester display name
     */
    private String requesterDisplayName;

    /**
     * request identifier (incremental id not object id)
     */
    private String requestId;

    /**
     * request priority
     */
    private String priority;

    /**
     * execution status
     */
    private IdentityRequest.ExecutionStatus executionStatus;

    /**
     * created date
     */
    private Date createdDate;

    /**
     * end date
     */
    private Date endDate;

    /**
     * Date request was terminated (canceled). Only set if Terminated.
     */
    private Date terminatedDate;

    /**
     * list of request items
     */
    private List<IdentityRequestItemDTO> items;

    /**
     * object id
     */
    private String id;

    /**
     * External ticket id
     */
    private String externalTicketId;

    /**
     * True if the request can be canceled, otherwise false.
     */
    private boolean cancelable;

    /**
     * Current "state" of the request, usually the workflow step name
     */
    private String state;

    /**
     * Completion status of the request
     */
    private IdentityRequest.CompletionStatus completionStatus;

    /**
     * Date that verification is completed.
     */
    private Date verificationDate;

    /**
     * List of policy violations
     */
    private List<PolicyViolation> policyViolations = new ArrayList<>();

    /**
     * List of Interactions
     */
    private List<ApprovalSummaryDTO> interactions = new ArrayList<>();

    /**
     * Constructor that takes IdentityRequest object
     *
     * @param identityRequest The IdentityRequest object
     */
    public IdentityRequestDTO(IdentityRequest identityRequest) throws InvalidParameterException {
        if (identityRequest == null) {
            // can't create dto without object
            throw new InvalidParameterException("identityRequest");
        }

        this.id = identityRequest.getId();
        this.type = identityRequest.getType();
        this.targetDisplayName = identityRequest.getTargetDisplayName();
        this.requesterDisplayName = identityRequest.getRequesterDisplayName();
        this.requestId = identityRequest.getName();
        this.priority = identityRequest.getPriority().name();
        this.executionStatus = identityRequest.getExecutionStatus();
        this.endDate = identityRequest.getEndDate();
        this.createdDate = identityRequest.getCreated();
        this.verificationDate = identityRequest.getVerified();
        this.completionStatus = identityRequest.getCompletionStatus();
        this.state = identityRequest.getState();
        if (IdentityRequest.ExecutionStatus.Terminated.equals(this.executionStatus)) {
            this.terminatedDate = (Date)identityRequest.getAttribute(IdentityRequest.ATT_TERMINATED_DATE);
        }
        initPolicyViolations(identityRequest);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Getters/Setters
    //
    //////////////////////////////////////////////////////////////////////

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTargetDisplayName() {
        return targetDisplayName;
    }

    public void setTargetDisplayName(String targetDisplayName) {
        this.targetDisplayName = targetDisplayName;
    }

    public String getRequesterDisplayName() {
        return requesterDisplayName;
    }

    public void setRequesterDisplayName(String requesterDisplayName) {
        this.requesterDisplayName = requesterDisplayName;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public IdentityRequest.ExecutionStatus getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(IdentityRequest.ExecutionStatus executionStatus) {
        this.executionStatus = executionStatus;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public List<IdentityRequestItemDTO> getItems() {
        return items;
    }

    public void setItems(List<IdentityRequestItemDTO> items) {
        this.items = items;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public boolean isCancelable() {
        return cancelable;
    }

    public void setCancelable(boolean cancelable) {
        this.cancelable = cancelable;
    }

    public Date getTerminatedDate() {
        return terminatedDate;
    }

    public void setTerminatedDate(Date terminatedDate) {
        this.terminatedDate = terminatedDate;
    }

    public String getExternalTicketId() {
        return externalTicketId;
    }

    public void setExternalTicketId(String externalTicketId) {
        this.externalTicketId = externalTicketId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public IdentityRequest.CompletionStatus getCompletionStatus() {
        return completionStatus;
    }

    public void setCompletionStatus(IdentityRequest.CompletionStatus completionStatus) {
        this.completionStatus = completionStatus;
    }

    public Date getVerificationDate() {
        return verificationDate;
    }

    public void setVerificationDate(Date verificationDate) {
        this.verificationDate = verificationDate;
    }

    public List<PolicyViolation> getPolicyViolations() {
        return policyViolations;
    }

    public void setPolicyViolations(List<PolicyViolation> policyViolations) {
        this.policyViolations = policyViolations;
    }

    public List<ApprovalSummaryDTO> getInteractions() {
        return this.interactions;
    }

    public void setInteractions(List<ApprovalSummaryDTO> interactions) {
        this.interactions = interactions;
    }

    /**
     * Find an IdentityRequestItemDTO for the given id
     * @param itemId ID of the identity request item
     * @return the IdentityRequestItemDTO matching the ID
     * @throws InvalidParameterException
     */
    public IdentityRequestItemDTO getItemDTO(String itemId)
        throws InvalidParameterException {
        if (Util.isNothing(itemId)) {
            throw new InvalidParameterException("itemId");
        }
        for (IdentityRequestItemDTO item : Util.iterate(this.items)) {
            if (Util.nullSafeEq(itemId, item.getId())) {
                return item;
            }
        }

        return null;
    }

    /**
     * initialize the list of policy violation dtos
     */
    private void initPolicyViolations(IdentityRequest identityRequest) {
        List<Map<String,Object>> policyViolationMaps = identityRequest.getPolicyViolationMaps();

        for (Map<String, Object> policyViolationObject : Util.safeIterable(policyViolationMaps)) {
            String policyName = (String)policyViolationObject.get("policyName");
            String policyType = (String)policyViolationObject.get("policyType");
            String ruleName = (String)policyViolationObject.get("ruleName");
            policyViolations.add(new PolicyViolation(policyName, policyType, ruleName));
        }
    }
}