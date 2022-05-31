/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 *
 * A DTO to represent the ViolationReviewWorkItemDTO model.
 * 
 * It gives some methods to the ui tier about the types of items
 * in the ViolationReviewWorkItemDTO.  
 *
 */
package sailpoint.web.workitem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.object.ApprovalItem;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.WorkItem;
import sailpoint.service.ApprovalItemsService;
import sailpoint.service.classification.ClassificationService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

public class ViolationReviewWorkItemDTO extends WorkItemDTO {

    private Boolean allowRequestsWithViolations;
    private List<Map<String, Object>> violations;
    private List<ApprovalItemDTO> requestedItems;
    private Boolean requireViolationReviewComments;
    
    /* constant for violation review workitem*/
    public static final String REVIEW_ITEM_ALLOW_VIOLATIONS = "allowRequestsWithViolations";
    public static final String REQUIRE_VIOLATION_COMMENT = "requireViolationReviewComments";
    /**
     * construct a workitem DTO
     * This constructor should only be called by WorkItemDTOFactory!
     * @param workitem
     */
    protected ViolationReviewWorkItemDTO(UserContext userContext, WorkItem workitem)
        throws GeneralException {

        super(userContext, workitem);
        this.allowRequestsWithViolations = workitem.getAttributes().getBoolean(REVIEW_ITEM_ALLOW_VIOLATIONS);  
        this.violations =  buildViolations(workitem);
        this.requireViolationReviewComments = workitem.getAttributes().getBoolean(REQUIRE_VIOLATION_COMMENT);  
    }
    
    /**
     * 
     * @return true if allow requests process with violations
     */
    public Boolean isAllowRequestsWithViolations() {
        return allowRequestsWithViolations;
    }
    
    /**
     * 
     * @return violations
     */
    public List<Map<String, Object>> getViolations() {
        return violations;
    }
    
    public List<ApprovalItemDTO> getRequestedItems() {
        return requestedItems;
    } 
    
    /**
     * 
     * @return list of violation map
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> buildViolations(WorkItem workItem) {
        List<Map<String, Object>> violationList = new ArrayList<Map<String, Object>>();
        if (workItem != null && workItem.getAttributes() != null) {
            violationList = workItem.getAttributes().getList(WorkItem.ATT_POLICY_VIOLATIONS);
        }
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        // All the consumers of this method were adding this attribute to the violation map,
        // so just do it here and save the trouble
        for (Map<String, Object> violation : Util.safeIterable(violationList)) {
            Map<String, Object> resultViolation = new HashMap<String, Object>(violation);
            resultViolation.put("workitemId", workItem.getId());
            result.add(resultViolation);
        }
        return result;
    }
    
    public static List<Map<String, Object>> buildViolations(String workItemId, Attributes<String, Object> attributes) {
        List<Map<String, Object>> violationList = new ArrayList<>();
        if (attributes != null) violationList = attributes.getList(WorkItem.ATT_POLICY_VIOLATIONS);
            
        List<Map<String, Object>> result = new ArrayList<>();
        // All the consumers of this method were adding this attribute to the violation map,
        // so just do it here and save the trouble
        for (Map<String, Object> violation : Util.safeIterable(violationList)) {
            Map<String, Object> resultViolation = new HashMap<>(violation);
            resultViolation.put("workitemId", workItemId);
            result.add(resultViolation);
        }
        return result;
    }

    /**
     * This method should only be called by WorkItemDTOFactory function
     * @param requestedItems
     */
    protected void setRequestedItems(List<ApprovalItemDTO> requestedItems) {
        this.requestedItems = requestedItems;
    }

    /**
     * Is comment required if submit violation is used
     * @return requireViolationComment
     */
    public Boolean isRequireViolationComment() {
        return requireViolationReviewComments;
    }


    /**
     * Class for encapsulating an approval item
     */
    public static class ApprovalItemDTO {
        private String id;
        private String entitlementApplication;
        private String entitlementName;
        private String entitlementValue;
        private String roleName;
        private String accountName;
        private String operation;
        private String state;
        private String roleId;
        private Attributes<String, Object> attributes;
        private List<String> classificationNames;
        private String targetItemId;

        /**
         * Constructor for creating DTO from real objects
         * This constructor should only be called by WorkItemDTOFactory
         * @param approvalItem The ApprovalItem to DTO-ify
         */
        protected ApprovalItemDTO(SailPointContext context, ApprovalItem approvalItem, String accountName, String roleId,
                                  String identityRequestId) throws GeneralException {
            ApprovalItemsService approvalItemsService = new ApprovalItemsService(context, approvalItem);

            this.id = approvalItem.getId();
            if(approvalItem.getName().equals(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES) ||
                    approvalItem.getName().equals(ProvisioningPlan.ATT_IIQ_DETECTED_ROLES)) {
                this.roleName = approvalItem.getDisplayValue();
            } else {
                this.entitlementApplication = approvalItem.getApplicationName();
                this.entitlementName = approvalItem.getName();
                this.entitlementValue = approvalItem.getDisplayableValue();
            }

            this.accountName = accountName;
            this.roleId = roleId;
            this.operation = approvalItem.getOperation();
            if(approvalItem.getState() != null) {
                this.state = approvalItem.getState().toString();
            } else {
                this.state = WorkItem.State.Pending.toString();
            }

            if (null != approvalItem.getAttributes()) {
                this.attributes = new Attributes<String,Object>(approvalItem.getAttributes());
            }

            this.targetItemId = approvalItemsService.getTargetItemId(identityRequestId);
            if (this.targetItemId != null) {
                Boolean classificationsEnabled = Configuration.getSystemConfig().getBoolean(Configuration.DISPLAY_CLASSIFICATIONS_IN_ACCESS_REQUEST);
                if (classificationsEnabled) {
                    ClassificationService classificationService = new ClassificationService(context);
                    this.classificationNames = classificationService.getClassificationNames(approvalItemsService.isRole() ?
                            Bundle.class : ManagedAttribute.class, this.targetItemId);
                }
            }
        }

        /**
         * The approval item's id
         * @return the id of the approval item
         */
        public String getId() {
            return id;
        }

        /**
         * From WorkItem.State enum will be Pending or Rejected
         * @return the state
         */
        public String getState() {
            return state;
        }

        /**
         * The operation, either "Add" or "Remove".
         * @return either Add or Remove
         */
        public String getOperation() {
            return operation;
        }

        /**
         * Returns the account name
         * @return The account name
         */
        public String getAccountName() {
            return accountName;
        }

        /**
         * Returns the role name or null if is entitlement
         * @return the role name or null if is entitlement
         */
        public String getRoleName() {
            return roleName;
        }

        /**
         * The entitlement value or null if role
         * @return The entitlement value
         */
        public String getEntitlementValue() {
            return entitlementValue;
        }

        /**
         * The entitlement name or null if role
         * @return The entitlement name
         */
        public String getEntitlementName() {
            return entitlementName;
        }

        /**
         * The entitlement application or null if role
         * @return The entitlement application
         */
        public String getEntitlementApplication() {
            return entitlementApplication;
        }

        /**
         * Returns true if is an entitlement request.
         * Determined by at least one of the entitlement fields not being null.
         * @return True if is an entitlement request
         */
        public boolean isEntitlementRequest() {
            return getEntitlementName() != null || getEntitlementValue() != null || getEntitlementApplication() != null;
        }

        /**
         * Returns true if this is a role request.
         * @return True if this is a role request
         */
        public boolean isRoleRequest() {
            return getRoleName() != null;
        }

        /**
         *
         * @return
         */
        public String getRoleId() {
            return roleId;
        }

        /**
         *
         * @param roleId
         */
        protected void setRoleId(String roleId) {
            this.roleId = roleId;
        }

        /**
         * 
         * @return extended attributes
         */
        public Attributes<String, Object> getAttributes() {
            return attributes;
        }

        /**
         * Set extended attributes values from factory
         * @param attributes
         */
        protected void setAttributes(Attributes<String, Object> attributes) {
            this.attributes = attributes;
        }

        /**
         * Merge all of the attributes in the given map into this ApprovalItemDTO.
         *
         * @param attrs  The possibly null attributes to merge.
         */
        protected void mergeAttributes(Attributes<String,Object> attrs) {
            if (null != attrs) {
                if (null == this.attributes) {
                    this.attributes = new Attributes<String,Object>();
                }
                this.attributes.putAll(attrs);
            }
        }

        public List<String> getClassificationNames() {
            return classificationNames;
        }

        public void setClassificationNames(List<String> classificationNames) {
            this.classificationNames = classificationNames;
        }
    }
}