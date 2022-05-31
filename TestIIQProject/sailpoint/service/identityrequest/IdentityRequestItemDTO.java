/*
 * (c) Copyright 2017. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.identityrequest;

import sailpoint.object.ApprovalItem;
import sailpoint.object.Attributes;
import sailpoint.object.ColumnConfig;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.WorkItem;
import sailpoint.service.AttachmentConfigDTO;
import sailpoint.service.AttachmentDTO;
import sailpoint.service.BaseDTO;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.MessageKeyHolder;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.tools.InvalidParameterException;
import sailpoint.web.view.IdentitySummary;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * IdentityRequestItem DTO
 */
public class IdentityRequestItemDTO extends BaseDTO {

    /**
     * Representation of an approval for a single item
     */
    public static class ApprovalItemSummary {

        /**
         * ID of the Approval WorkItem
         */
        private String workItemId;

        /**
         * ID of the ApprovalItem
         */
        private String approvalItemId;

        /**
         * Owner of the approval WorkItem
         */
        private IdentitySummary owner;

        /**
         * Count of comments on the approval item in the approval work item
         */
        private int commentCount;

        /**
         * Date the approval work item was created
         */
        private Date created;

        /**
         * Can access comments
         */
        private boolean canAccessComments;

        /**
         * Constructor
         * @param approvalWorkItem WorkItem from the approval
         * @param approvalItemId ID of the ApprovalItem in the WorkItem approval set
         * @param commentCount Count of comments for the approval item
         * @throws InvalidParameterException
         */
        public ApprovalItemSummary(WorkItem approvalWorkItem, String approvalItemId, int commentCount)
                throws InvalidParameterException{
            if (approvalWorkItem == null) {
                throw new InvalidParameterException("approvalWorkItem");
            }

            if (approvalItemId == null) {
                throw new InvalidParameterException("approvalItemId");
            }

            this.workItemId = approvalWorkItem.getId();
            this.created= approvalWorkItem.getCreated();
            this.approvalItemId = approvalItemId;
            this.owner = new IdentitySummary(approvalWorkItem.getOwner());
            this.commentCount = commentCount;
        }

        public String getWorkItemId() {
            return workItemId;
        }

        public String getApprovalItemId() {
            return approvalItemId;
        }

        public void setApprovalItemId(String approvalItemId) {
            this.approvalItemId = approvalItemId;
        }

        public IdentitySummary getOwner() {
            return owner;
        }

        public int getCommentCount() {
            return commentCount;
        }

        public void setCommentCount(int commentCount) {
            this.commentCount = commentCount;
        }

        public Date getCreated() {
            return created;
        }

        public boolean isCanAccessComments() {
            return canAccessComments;
        }

        public void setCanAccessComments(boolean canAccessComments) {
            this.canAccessComments = canAccessComments;
        }
    }

    /**
     * Operation type enum - This is a combined list of ProvisioningPlan.Operation and ProvisioningPlan.ObjectOperation
     */
    public enum Operation implements MessageKeyHolder {
        Set(MessageKeys.PROVISIONING_PLAN_OP_SET),
        Add(MessageKeys.PROVISIONING_PLAN_OP_ADD),
        Remove(MessageKeys.PROVISIONING_PLAN_OP_REMOVE),
        Revoke(MessageKeys.PROVISIONING_PLAN_OP_REVOKE),
        Retain(MessageKeys.PROVISIONING_PLAN_OP_RETAIN),
        Create(MessageKeys.RESOURCE_OBJECT_OP_CREATE),
        Modify(MessageKeys.RESOURCE_OBJECT_OP_MODIFY),
        Delete(MessageKeys.RESOURCE_OBJECT_OP_DELETE),
        Disable(MessageKeys.RESOURCE_OBJECT_OP_DISABLE),
        Enable(MessageKeys.RESOURCE_OBJECT_OP_ENABLE),
        Unlock(MessageKeys.RESOURCE_OBJECT_OP_UNLOCK),
        Lock(MessageKeys.RESOURCE_OBJECT_OP_LOCK);

        private String messageKey;

        Operation(String messageKey) { this.messageKey = messageKey; }

        public String getMessageKey() { return this.messageKey; }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    /**
     * request operation
     */
    private Operation operation;

    /**
     * application name
     */
    private String applicationName;

    /**
     * true if request is for role
     */
    private boolean role;

    /**
     * true if request is for entitlement
     */
    private boolean entitlement;

    /**
     * true if request is for an entitlement that has a managed attribute
     */
    private boolean hasManagedAttribute;

    /**
     * request related account name
     */
    private String accountName;

    /**
     * Application instance, if applicable.
     */
    private String instance;

    /**
     * approval state
     */
    private WorkItem.State approvalState;

    /**
     * provisioning state
     */
    private ApprovalItem.ProvisioningState provisioningState;

    /**
     * name of attribute or target to be provisioned
     */
    private String name;

    /**
     * value of attribute or target to be provisioned
     */
    private String value;

    /**
     * Displayable value
     */
    private String displayableValue;

    /**
     * List of approval item summaries
     */
    private List<ApprovalItemSummary> approvalItemSummaries;

    /**
     * Displayable account name
     */
    private String displayableAccountName;

    /**
     * Start date for the item. AKA sunrise date.
     */
    private Date startDate;

    /**
     * End date for the item. AKA sunset date.
     */
    private Date endDate;

    /**
     * Comments made by the requester
     */
    private String requesterComments;

    /**
     * Name of the provisioning engine for the item
     */
    private String provisioningEngine;

    /**
     * Count of retries for the item, if any.
     */
    private Integer retries;

    /**
     * Compilation status of the item. Null for a directly requested item.
     */
    private IdentityRequestItem.CompilationStatus compilationStatus;

    /**
     * Provisioning request ID, optionally set by an integration.
     */
    private String provisioningRequestId;

    /**
     * ID of assignment
     */
    private String assignmentId;

    /**
     * Flag used by the UI to limit view access to attachments
     */
    private boolean canViewAttachments;

    private List<AttachmentConfigDTO> attachmentConfigList;
    private List<AttachmentDTO> attachments;



    private List<String> classificationNames;

    public static final String ATTRIBUTES = "attributes";
    public static final String ATTACHMENTS_LIST = "attachments";
    public static final String ATTACHMENT_CONFIG_LIST = "attachmentConfigList";
    public static final String ASSIGNMENT_ID = "assignmentId";

    /**
     * Constructor that takes the IdentityRequestItem object
     * @param item The IdentityRequestItem object
     */
    public IdentityRequestItemDTO(IdentityRequestItem item) throws InvalidParameterException {
        if (item == null) {
            // can't create dto without object
            throw new InvalidParameterException("item");
        }

        setId(item.getId());
        this.operation = Operation.valueOf(item.getOperation());
        this.applicationName = item.getApplication();
        this.instance = item.getInstance();
        this.name = item.getName();
        this.value = (String)item.getValue();
        this.approvalState = item.getApprovalState();
        this.provisioningState = item.getProvisioningState();
        this.accountName = item.getNativeIdentity();
        this.startDate = item.getStartDate();
        this.endDate = item.getEndDate();
        this.provisioningEngine = item.getProvisioningEngine();
        this.retries = item.getRetries();
        this.compilationStatus = item.getCompilationStatus();
        this.provisioningRequestId = item.getProvisioningRequestId();
        this.attachmentConfigList = (List) item.getAttribute(ATTACHMENT_CONFIG_LIST);
        this.attachments = (List) item.getAttribute(ATTACHMENTS_LIST);
        this.assignmentId = (String) item.getAttribute(ASSIGNMENT_ID);
        checkValues();
    }

    /**
     * Column config constructor
     * @param object Map representation of the object
     * @param cols List of ColumnConfigs to fill out the dto
     * @param additionalColumns Projection columns always included
     */
    public IdentityRequestItemDTO(Map<String,Object> object, List<ColumnConfig> cols, List<String> additionalColumns) {
        super(object, cols, additionalColumns);
        // always populate assignmentId for permission checks in IdentityRequestItemListService
        Attributes attributes = (Attributes) object.get(ATTRIBUTES);
        if (attributes != null && attributes.get(ASSIGNMENT_ID) != null) {
            this.assignmentId = (String) attributes.get(ASSIGNMENT_ID);
        }
        checkValues();
    }

    /**
     * Do some cleanup on values based on what was set.
     */
    private void checkValues() {
        // Show the "branded" IIQ app name
        if ( Util.nullSafeEq(this.applicationName, ProvisioningPlan.APP_IIQ, false) ) {
            this.applicationName = BrandingServiceFactory.getService().getApplicationName();
        }
        // Show the "branded" IIQ provisioning engine name
        if ( Util.nullSafeEq(this.provisioningEngine, ProvisioningPlan.APP_IIQ, false) ) {
            this.provisioningEngine = BrandingServiceFactory.getService().getApplicationName();
        }
    }

    @Override
    protected boolean writeProperty(String name, Object value) {
        // Special case. Operation is stored as a string, but we want to set the Operation enum of that value
        if ("operation".equals(name) && value != null) {
            setOperation(Operation.valueOf((String)value));
            return true;
        }

        return super.writeProperty(name, value);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Getters/Setters
    //
    //////////////////////////////////////////////////////////////////////

    public Operation getOperation() {
        return operation;
    }

    public void setOperation(Operation operation) {
        this.operation = operation;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public boolean isRole() {
        return this.role;
    }

    public void setRole(boolean role) {
        this.role = role;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public WorkItem.State getApprovalState() {
        return approvalState;
    }

    public void setApprovalState(WorkItem.State approvalState) {
        this.approvalState = approvalState;
    }

    public ApprovalItem.ProvisioningState getProvisioningState() {
        return provisioningState;
    }

    public void setProvisioningState(ApprovalItem.ProvisioningState provisioningState) {
        this.provisioningState = provisioningState;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean isEntitlement() {
        return this.entitlement;
    }

    public void setEntitlement(boolean entitlement) {
        this.entitlement = entitlement;
    }

    public boolean isHasManagedAttribute() { return this.hasManagedAttribute; }

    public void setHasManagedAttribute(boolean hasManagedAttribute) { this.hasManagedAttribute = hasManagedAttribute; }

    public void addApprovalItemSummary(ApprovalItemSummary summary) {
        if (this.approvalItemSummaries == null) {
            this.approvalItemSummaries = new ArrayList<>();
        }

        this.approvalItemSummaries.add(summary);
    }

    public List<ApprovalItemSummary> getApprovalItemSummaries() {
        return this.approvalItemSummaries;
    }

    public String getDisplayableValue() {
        return displayableValue;
    }

    public void setDisplayableValue(String displayableValue) {
        this.displayableValue = displayableValue;
    }

    public String getDisplayableAccountName() {
        return displayableAccountName;
    }

    public void setDisplayableAccountName(String displayableAccountName) {
        this.displayableAccountName = displayableAccountName;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public String getRequesterComments() {
        return requesterComments;
    }

    public void setRequesterComments(String requesterComments) {
        this.requesterComments = requesterComments;
    }

    public String getProvisioningEngine() {
        return provisioningEngine;
    }

    public void setProvisioningEngine(String provisioningEngine) {
        this.provisioningEngine = provisioningEngine;
    }

    public Integer getRetries() {
        return retries;
    }

    public void setRetries(Integer retries) {
        this.retries = retries;
    }

    public IdentityRequestItem.CompilationStatus getCompilationStatus() {
        return compilationStatus;
    }

    public void setCompilationStatus(IdentityRequestItem.CompilationStatus compilationStatus) {
        this.compilationStatus = compilationStatus;
    }

    public String getProvisioningRequestId() {
        return provisioningRequestId;
    }

    public void setProvisioningRequestId(String provisioningRequestId) {
        this.provisioningRequestId = provisioningRequestId;
    }

    /**
     * Return the attachment configs associated with the attachments.
     */
    public List<AttachmentConfigDTO> getAttachmentConfigList() { return this.attachmentConfigList; }

    /**
     * Set the attachment prompt
     * @param prompt
     */
    public void setAttachmentConfigList(List<AttachmentConfigDTO> prompt) { this.attachmentConfigList = prompt; }

    /**
     * Return a list of attached files.
     */
    public List<AttachmentDTO> getAttachments() { return this.attachments; }

    /**
     * Set the list of attachments
     * @param attachments
     */
    public void setAttachments(List<AttachmentDTO> attachments) {
        this.attachments = attachments;
    }

    /**
     * @return list of classification names
     */
    public List<String> getClassificationNames() {
        return classificationNames;
    }

    /**
     * Set classification names list
     * @param classificationNames
     */
    public void setClassificationNames(List<String> classificationNames) {
        this.classificationNames = classificationNames;
    }

    /**
     *
     * @return AssignmentId
     */
    public String getAssignmentId() {
        return assignmentId;
    }

    /**
     * Set assignmentId
     * @param assignmentId
     */
    public void setAssignmentId(String assignmentId) {
        this.assignmentId = assignmentId;
    }

    /**
     *
     * @return true if user can view attachments
     */
    public boolean isCanViewAttachments() {
        return canViewAttachments;
    }

    /**
     * Sets the canViewAttachments flag
     * @param canViewAttachments
     */
    public void setCanViewAttachments(boolean canViewAttachments) {
        this.canViewAttachments = canViewAttachments;
    }
}
