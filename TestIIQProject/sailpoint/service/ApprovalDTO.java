/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sailpoint.api.ObjectUtil;
import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalSet;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.BundleDifference;
import sailpoint.object.Capability;
import sailpoint.object.ChangeSummary;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.NativeChangeDetection;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.WorkItem;
import sailpoint.recommender.RecommendationDTO;
import sailpoint.service.classification.ClassificationService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.util.WebUtil;
import sailpoint.web.workitem.ViolationReviewWorkItemDTO;
import sailpoint.web.workitem.WorkItemDTO;
import sailpoint.workflow.IdentityLibrary;

import static sailpoint.service.identityrequest.IdentityRequestItemListService.ATTR_ASSIGNED_ROLES;
import static sailpoint.service.identityrequest.IdentityRequestItemListService.ATTR_DETECTED_ROLES;

/**
 * A DTO that holds information about an approval work item.
 */
public class ApprovalDTO extends WorkItemDTO {

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * A representation of a single approval item.
     */
    public static class ApprovalItemDTO extends BaseDTO {
        
        // ColumnConfig to render the sunrise/sunset date. Need to set this attribute so it will render when needed,
        // otherwise the card data directive will exclude it.
        public static final String SUNRISE_SUNSET_ATTR = "sunriseSunset";
        public static final String SUNSET_ONLY_ATTR = "sunsetOnly";
        public static final String ATTACHMENTS_LIST = "attachments";
        public static final String ATTACHMENT_CONFIG_LIST = "attachmentConfigList";

        /* Enumeration to easily provide type of approval item to UI */
        public static enum Type {
            Entitlement,
            Role,
            Account,
            BatchRequest
        }

        /* Enumeration for ApprovalItem decisions */
        public static enum Decision {
            Approved,
            Rejected
        }

        private String id;
        private IdentitySummaryDTO owner;
        private Long sunrise;
        private Long sunset;
        private boolean sunsetExpired;
        private boolean hadSunriseSunset;
        private int commentCount;
        private String assignmentNote;

        private String operation;
        private Decision decision;
        private Type itemType;

        private String application;
        private String nativeIdentity;
        private String accountDisplayName;
        private boolean newAccount;
        private String name;
        private Object value;
        private String displayName;
        private String displayValue;
        private String description;
        private Integer riskScoreWeight;

        private List<AttachmentConfigDTO> attachmentConfigList;
        private List<AttachmentDTO> attachments;
        private boolean canViewAttachments;
        private RecommendationDTO recommendation;

        private List<String> classifications;
        private String targetItemId;

        /**
         * Constructor.
         * @param  userContext     The UserContext.
         * @param  workItemId      The approval work item id.
         * @param  item            The approval item.
         * @param  targetIdentity  The identity being requested for.
         */
        public ApprovalItemDTO(UserContext userContext, String workItemId, ApprovalItem item,
                                   Identity targetIdentity) throws GeneralException {
            ApprovalItemsService approvalItemsService =
                new ApprovalItemsService(userContext.getContext(), item);

            WorkItemService svc = new WorkItemService(workItemId, userContext, true);
            String identityRequestId = svc.getWorkItem().getIdentityRequestId();

            this.id = item.getId();
            this.owner = createIdentitySummary(approvalItemsService.getOwner(identityRequestId));
            this.sunrise = (item.getStartDate() == null) ? null : item.getStartDate().getTime();
            this.sunset = (item.getEndDate() == null) ? null : item.getEndDate().getTime();
            this.sunsetExpired = isSunsetExpired(item.getEndDate());
            if (this.sunrise != null || this.sunset != null) {
                this.setAttribute(SUNRISE_SUNSET_ATTR, true);
                // For remove operations, we only allow setting sunset
                this.setAttribute(SUNSET_ONLY_ATTR, ProvisioningPlan.Operation.Remove.name().equals(item.getOperation()));
            }
            this.hadSunriseSunset = Util.otob(item.getAttribute(ApprovalItemsService.ATT_HAD_SUNRISE_SUNSET));
            this.commentCount = approvalItemsService.getCommentCount();

            //divert logic to service but only if targetIdentity is not null
            if (null != targetIdentity) {
                ApprovalWorkItemService workItemService = new ApprovalWorkItemService(workItemId, userContext);
                this.assignmentNote = WebUtil.escapeComment(workItemService.getAssignmentNote(item.getId(), targetIdentity));
                this.accountDisplayName =
                        approvalItemsService.getAccountDisplayName(targetIdentity, userContext.getLocale(), userContext.getUserTimeZone());
            }

            this.operation = approvalItemsService.getOperation(userContext.getLocale(), userContext.getUserTimeZone());
            this.decision = getDecision(item);
            this.itemType = getItemType(item);

            this.application = approvalItemsService.getApplicationDisplayName();
            this.nativeIdentity = item.getNativeIdentity();
            this.newAccount = approvalItemsService.isForceNewAccount();
            this.name = item.getName();
            this.value = item.getValue();
            this.displayName = item.getDisplayName();
            // For a batchRequest approval type, the displayValue contains the fileName
            if (null != item.getAttribute(ApprovalItemsService.ATT_BATCH_REQUEST)) {
                this.displayValue = item.getDisplayValue();
            }
            else {
                this.displayValue = approvalItemsService.getDisplayValue(userContext.getLocale());
            }
            this.description = approvalItemsService.getDescription(userContext.getLocale());
            this.riskScoreWeight = approvalItemsService.getRiskScoreWeight(identityRequestId);
            this.attachmentConfigList = (List) item.getAttribute(ATTACHMENT_CONFIG_LIST);
            this.attachments = (List) item.getAttribute(ATTACHMENTS_LIST);
            this.canViewAttachments = checkAttachmentPermissions(userContext, workItemId);
            if (item.getRecommendation() != null) {
                this.recommendation = new RecommendationDTO(userContext.getContext(), item.getRecommendation(), userContext.getLocale(), userContext.getUserTimeZone());
            }

            this.targetItemId = approvalItemsService.getTargetItemId(identityRequestId);
            if (this.targetItemId != null) {
                ClassificationService classificationService = new ClassificationService(userContext.getContext());
                this.classifications = classificationService.getClassificationNames(approvalItemsService.isRole() ? Bundle.class : ManagedAttribute.class, this.targetItemId);
            }
        }
        
        /**
         * Determines if the sunset date is expired or not.
         *
         * @param date The date.
         * @return True if expired, false otherwise.
         */
        private boolean isSunsetExpired(Date date) {
            if (sunset == null) {
                return false;
            }

            // today is NOT considered expired so check for less than zero
            return Util.getDaysDifference(date, new Date()) < 0;
        }

        /**
         * Convert the WorkItem.State in the ApprovalItem to a nicer 
         * ApprovalItemDecision type
         * @param item ApprovalItem
         * @return ApprovalItemDecision
         */
        private static Decision getDecision(ApprovalItem item) {
            if (item.isApproved()) {
                return Decision.Approved;
            } else if (item.isRejected()) {
                return Decision.Rejected;
            }
            
            return null;
        }

        /**
         * Get the ApprovalItemType for the given approval item 
         * 
         * @param item ApprovalItem
         * @return ApprovalItemType
         */
        private static Type getItemType(ApprovalItem item) {
            Type type;

            if (item != null && ApprovalListService.ACCOUNTS_REQUEST_TYPES.contains(item.getAttribute(IdentityLibrary.VAR_FLOW))) {
               type = Type.Account; 
            }
            else if (null != item.getAttribute(ApprovalItemsService.ATT_BATCH_REQUEST)) {
               type = Type.BatchRequest;
            }
            else if (ApprovalItemsService.isRole(item)) {
                type = Type.Role;
            }
            else if (ApprovalItemsService.isCreate(item) || ApprovalItemsService.isLifecycleDeleteOrDisableOperation(item)) {
                type = Type.Account;
            }
            else {
                type = Type.Entitlement;
            }

            return type;
        }

        private boolean checkAttachmentPermissions(UserContext userContext, String workItemId) throws GeneralException {
            boolean canViewAttachments = false;
            ApprovalWorkItemService workItemService = new ApprovalWorkItemService(workItemId, userContext);
            WorkItem workItem = workItemService.getWorkItem();
            Identity loggedInUser = userContext.getLoggedInUser();
            if (Capability.hasSystemAdministrator(userContext.getLoggedInUserCapabilities()) ||
                loggedInUser.equals(workItem.getOwner()) || loggedInUser.isInWorkGroup(workItem.getOwner())) {
                canViewAttachments = true;
            }
             return canViewAttachments;
        }

        /**
         * Return the ID of the approval item.
         */
        @Override
        public String getId() {
            return id;
        }

        /**
         * Return the owner of the data the is being represented by this item - for instance the
         * role owner, entitlement owner, etc...
         */
        public IdentitySummaryDTO getOwner() {
            return owner;
        }

        /**
         * Return the sunrise as a long, or null if there is not a sunrise date.
         */
        public Long getSunrise() {
            return sunrise;
        }

        /**
         * Return the sunset as a long, or null if there is not a sunset date.
         */
        public Long getSunset() {
            return sunset;
        }

        /**
         * Indicates whether or not the sunset date for the item has expired.
         */
        public boolean isSunsetExpired() {
            return sunsetExpired;
        }

        /**
         * Return whether this item has every had a sunrise/sunset date set on it.
         */
        public boolean isHadSunriseSunset() {
            return hadSunriseSunset;
        }

        /**
         * Return the number of comments on this item.
         */
        public int getCommentCount() {
            return commentCount;
        }

        /**
         * Return the assignment note for this item if it is a role.
         */
        public String getAssignmentNote() {
            return assignmentNote;
        }

        /**
         * Return a string representing the operation for this item - Add/Remove.
         */
        public String getOperation() {
            return operation;
        }

        /**
         * Return the decision for this item or null if no decision has been made yet.
         */
        public Decision getDecision() {
            return decision;
        }

        /**
         * Return the item type.
         */
        public Type getItemType() {
            return itemType;
        }

        /**
         * Return the displayable name of the application if this is an entitlement/account item.
         */
        public String getApplication() {
            return application;
        }

        /**
         * Return the native identity of the account if this is an entitlement/account item.
         */
        public String getNativeIdentity() {
            return nativeIdentity;
        }

        /**
         * Return the display name of the account if this is an entitlement/account item.
         */
        public String getAccountDisplayName() {
            return accountDisplayName;
        }

        /**
         * Return whether a new account is being created if this is an entitlement/account item.
         */
        public boolean isNewAccount() {
            return newAccount;
        }

        /**
         * Return the name of the item being requested.
         */
        public String getName() {
            return name;
        }

        /**
         * Return the value of the item being requested.
         */
        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        /**
         * Return the display name of the item being requested.
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Return the display value of the item being requested.
         */
        public String getDisplayValue() {
            return displayValue;
        }

        public void setDisplayValue(String displayValue) {
            this.displayValue = displayValue;
        }

        /**
         * Return the description of the item being requested.
         */
        public String getDescription() {
            return description;
        }

        /**
         * Return the risk score weight of the item being requested, or null if there is no score.
         */
        public Integer getRiskScoreWeight() {
            return riskScoreWeight;
        }

        /**
         * Return the list of prompts associated with the attachments.
         */
        public List<AttachmentConfigDTO> getAttachmentConfigList() { return this.attachmentConfigList; }

        /**
         * Return a list of attached files.
         */
        public List<AttachmentDTO> getAttachments() { return this.attachments; }

        /**
         * Get classifications
         */
        public List<String> getClassifications() {
            return classifications;
        }

        /**
         * Set classifications
         * @param classifications
         */
        public void setClassifications(List<String> classifications) {
            this.classifications = classifications;
        }

        /**
         * Get the target item id
         * @return String id
         */
        public String getTargetItemId() {
            return targetItemId;
        }

        /**
         * Set target item id
         * @param targetItemId
         */
        public void setTargetItemId(String targetItemId) {
            this.targetItemId = targetItemId;
        }

        /**
         * Set the canViewAttachments flag
         * @param canViewAttachments
         */
        public void setCanViewAttachments(boolean canViewAttachments) { this.canViewAttachments = canViewAttachments; }

        /**
         * Get the boolean canViewAttachments flag
         * @return true if the user can view attachments on this approval item.
         */
        public boolean isCanViewAttachments() { return this.canViewAttachments; }
        
	/** 
	 * @return the recommendation, if any, for this ApprovalItem
         */
        public RecommendationDTO getRecommendation() { return recommendation; }
    }

    /**
     * DTO holding details of an identity update through Identity Warehouse.
     */
    public static class IdentityUpdateDTO {
        private List<String> linksToMove;
        private List<String> linksToRemove;
        private List<String> linksToAdd;
        private List<String> oldRoles;
        private List<String> newRoles;
        private List<ApprovalItemDTO> attributeItems;

        @SuppressWarnings("unchecked")
        public IdentityUpdateDTO(UserContext userContext, WorkItem workItem) throws GeneralException {
            if (workItem.get(IdentityLibrary.ARG_LINKS_TO_MOVE) != null) {
                this.linksToMove = new ArrayList<>((List<String>) workItem.get(IdentityLibrary.ARG_LINKS_TO_MOVE));
            }

            if (workItem.get(IdentityLibrary.ARG_LINKS_TO_REMOVE) != null) {
                this.linksToRemove = new ArrayList<>((List<String>) workItem.get(IdentityLibrary.ARG_LINKS_TO_REMOVE));
            }

            if (workItem.get(IdentityLibrary.ARG_LINKS_TO_ADD) != null) {
                this.linksToAdd = new ArrayList<>((List<String>) workItem.get(IdentityLibrary.ARG_LINKS_TO_ADD));
            }

            if (workItem.get(IdentityLibrary.ARG_NEW_ROLES_VARIABLE) != null) {
                this.newRoles = new ArrayList<>((List<String>) workItem.get(IdentityLibrary.ARG_NEW_ROLES_VARIABLE));
            }

            if (workItem.get(IdentityLibrary.ARG_OLD_ROLES_VARIABLE) != null) {
                this.oldRoles = new ArrayList<>((List<String>) workItem.get(IdentityLibrary.ARG_OLD_ROLES_VARIABLE));
            }

            if (workItem.get(IdentityLibrary.ARG_APPROVAL_SET) != null) {
                ApprovalSet approvalSet = (ApprovalSet)workItem.get(IdentityLibrary.ARG_APPROVAL_SET);
                if (!Util.isEmpty(approvalSet.getItems())) {
                    this.attributeItems = ApprovalDTO.createApprovalItems(userContext, approvalSet, workItem.getId());
                }
            }
        }

        /**
         * String representations of the link moves. These are created in IdentityLibrary.LinkInfoGenerator
         */
        public List<String> getLinksToMove() {
            return linksToMove;
        }

        /**
         * String representations of the link moves. These are created in IdentityLibrary.LinkInfoGenerator
         */
        public List<String> getLinksToRemove() {
            return linksToRemove;
        }

        /**
         * String representations of the link moves. These are created in IdentityLibrary.LinkInfoGenerator
         * Not used anymore, but keep around in case of customization
         */
        public List<String> getLinksToAdd() {
            return linksToAdd;
        }

        /**
         * String representations of the roles being removed. These are created in IdentityLibrary.LinkInfoGenerator
         * Not used anymore, but keep around in case of customization
         */
        public List<String> getOldRoles() {
            return oldRoles;
        }

        /**
         * String representations of the roles being added. These are created in IdentityLibrary.LinkInfoGenerator
         * Not used anymore, but keep around in case of customization
         */
        public List<String> getNewRoles() {
            return newRoles;
        }

        /**
         * Approval items holding the identity/account attribute, capability and scope changes.
         * We separate it from the normal list of approval items in the ApprovalDTO so as not to confuse the UI,
         * since these are not actionable individually.
         */
        public List<ApprovalItemDTO> getAttributeItems() {
            return attributeItems;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** ColumnConfig to set calculated values based off the Attributes of the approval WorkItem */
    public static final String ATTRIBUTES_CALC_ATTR = "attributesCalc";
    
    /** enum to define sub-types of approval **/
    public static enum ApprovalType {
        AccessRequest,
        Role,
        // Identity Update workflow (from Identity Warehouse)
        Identity,
        ManageAttribute,
        NativeChange,
        Pam,
        ManageAccount,
        BatchRequest,
        Custom
    }
    
    /**
     * A list of violations that would be created if this request were approved.
     */
    private List<Map<String,Object>> violations;

    /**
     * A list of approval items for this approval.
     */
    private List<ApprovalItemDTO> approvalItems;

    /** 
     * Cached summary of bundle or profile differences. 
     */  
    private BundleDifference roleDifference;
  
    /**
     * get role target if it's role edit approval
     */
    private RoleSummaryDTO roleTarget;

    /** 
     * Task result Id 
     */  
    private String taskResultId;
  
    /** 
     * True if the current user can view pending changes 
     */  
    private boolean viewPendingChanges;

    /**
     * Workflow case id
     */
    private String workflowCaseId;
    
    /**
     * True if the work item has report result
     */
    private boolean hasReport;

    /**
     * True if the work item has form associated to it
     */
    private boolean hasForm;

    /**
     * True if the original form values were updated before making a decision on the approval workItem
     */
    private boolean formChanged;

    /**
     * The sub-types of approval
     */
    private ApprovalType approvalType;

    /**
     * Change summary
     */
    private ChangeSummary changes;

    /**
     * Summary name
     */
    private String summaryName;

    /**
     * DTO holding information about Identity Update approval
     */
    private IdentityUpdateDTO identityUpdate;

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.
     *
     * @param  userContext  The UserContext.
     * @param  approval     A map representation of the approval work item.
     * @param  cols         The list of column configs to include in the DTO.
     *
     * @throws GeneralException  If the approval map is missing a required field.
     * @throws ObjectNotFoundException  If the work item does not exist.
     */
    public ApprovalDTO(UserContext userContext, Map<String,Object> approval, List<ColumnConfig> cols)
        throws GeneralException, ObjectNotFoundException {

        super(userContext, approval, cols);

        if (WorkItemDTO.isErrorDTO(this)) {
            return;
        }

        ApprovalWorkItemService svc = new ApprovalWorkItemService(getId(), userContext);
        WorkItem workItem = svc.getWorkItem();
        if (null != workItem.getAttributes()) {
            this.setAttributesCalc(workItem.getAttributes());
        }

        // If we have an IdentityUpdateDTO set, we dont want to set any top level approval items
        if (svc.isIdentityUpdate()) {
            this.setIdentityUpdate(new IdentityUpdateDTO(userContext, workItem));
            this.setApprovalType(ApprovalType.Identity);
        } else {
            this.approvalItems = createApprovalItems(userContext, workItem.getApprovalSet(), getId());
        }
        this.setApprovalTypeforBatchRequest();
        this.fixRoleRenames(userContext);
    }

    /**
     * Get a list of ApprovalItemDTOs for the given work item.
     *
     * @param  userContext  The UserContext.
     * @param  approvalSet  ApprovalSet
     *
     * @return List of ApprovalItemDTOs.
     */
    protected static List<ApprovalItemDTO> createApprovalItems(UserContext userContext, ApprovalSet approvalSet, String id)
            throws GeneralException {

        List<ApprovalItemDTO> approvalItems = new ArrayList<>();
        WorkItemService svc = new WorkItemService(id, userContext, true);
        Identity targetIdentity;
        try {
            targetIdentity = svc.getTargetIdentity();
        } catch (ObjectNotFoundException ofne) {
            targetIdentity = null;
        }

        // Scrub passwords from the approval set in all cases, just in case.
        ApprovalSet scrubbed = (approvalSet == null) ? null : ObjectUtil.scrubPasswordsAndClone(approvalSet);
        List<ApprovalItem> items = scrubbed != null ? scrubbed.getItems() : Collections.emptyList();
        for (ApprovalItem item : Util.safeIterable(items)) {
            approvalItems.add(new ApprovalItemDTO(userContext, id, item, targetIdentity));
        }
        return approvalItems;
    }

    /**
     * check and set approval type for batch request approval
     */
    private void setApprovalTypeforBatchRequest() {
        if (this.approvalItems != null && !this.approvalItems.isEmpty()) {
            if (this.approvalItems.get(0).itemType == ApprovalItemDTO.Type.BatchRequest) {
                this.setApprovalType(ApprovalType.BatchRequest);
            }
        } 
    }
    /**
     * If a role is renamed between when the user requests it and when it is provisioned, the approval will have the old.
     * Here's what we do here:
     * - Loop through the approval items and look for any items that are for assigned roles or detected roles
     * - Do a query to find the roles by name, if we can't find all the roles, then there is at least one rename
     * - If we find a role name without a matching role, we load the identity request and try to find it from there
     *
     * @param userContext
     */
    private void fixRoleRenames(UserContext userContext) throws GeneralException {
        List<String> roleNames = new ArrayList<>();
        for (ApprovalDTO.ApprovalItemDTO itemDto : Util.iterate(this.getApprovalItems())) {
            if (Util.nullSafeEq(itemDto.getName(), ATTR_DETECTED_ROLES) || Util.nullSafeEq(itemDto.getName(), ATTR_ASSIGNED_ROLES)) {
                roleNames.add((String) itemDto.getValue());
            }
        }

        // If we have found that some of the approval items are for roles, try to fetch their names from the db
        if (roleNames.size() > 0) {
            QueryOptions ops = new QueryOptions();
            ops.addFilter(Filter.in("name", roleNames));
            Iterator<Object[]> it = userContext.getContext().search(Bundle.class, ops, "name");
            while (it.hasNext()) {
                Object[] row = it.next();
                roleNames.remove(row[0]);
            }
        }

        // At this point if there are any roles left in the array, we know we've got a rename, so look it up
        // using the role request and fix the name.
        if (roleNames.size() > 0) {
            IdentityRequest identityRequest = userContext.getContext().getObjectByName(IdentityRequest.class, getAccessRequestName());
            if (identityRequest != null) {
                for (String roleName : Util.iterate(roleNames)) {
                    for (IdentityRequestItem item : Util.iterate(identityRequest.getItems())) {
                        if (Util.nullSafeEq(item.getValue(), roleName)) {
                            String id = item.getStringAttribute("id");
                            if (id != null) {

                                // Look up the id of the role from the identity request item and replace the id
                                QueryOptions ops = new QueryOptions();
                                ops.addFilter(Filter.eq("id", id));
                                Iterator<Object[]> roles = userContext.getContext().search(Bundle.class, ops, "name, displayableName");
                                if (roles != null && roles.hasNext()) {
                                    Object[] role = roles.next();
                                    for (ApprovalDTO.ApprovalItemDTO itemDto : Util.iterate(this.getApprovalItems())) {
                                        if (Util.nullSafeEq(itemDto.getValue(), roleName)) {
                                            itemDto.setValue(role[0]);
                                            itemDto.setDisplayValue((String) role[1]);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public List<Map<String, Object>> getViolations() {
        return this.violations;
    }

    public List<ApprovalItemDTO> getApprovalItems() {
        return this.approvalItems;
    }

    public BundleDifference getRoleDifference() {
        return this.roleDifference;
    }

    public String getTaskResultId() {
        return this.taskResultId;
    }

    public boolean getViewPendingChanges() {
        return this.viewPendingChanges;
    }

    public RoleSummaryDTO getRoleTarget() {
        return this.roleTarget;
    }

    public String getWorkflowCaseId() {
        return this.workflowCaseId;
    }

    public boolean getHasReport() {
        return this.hasReport;
    }

    public ApprovalType getApprovalType() {
        return this.approvalType;
    }

    public IdentityUpdateDTO getIdentityUpdate() {
        return this.identityUpdate;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////
    public void setAttributesCalc(Attributes<String, Object> attrs) {
        this.violations = ViolationReviewWorkItemDTO.buildViolations(getId(), attrs);

        if ( null != attrs.get(WorkItem.ATT_FORM)) {
            this.setHasForm(true);
        }
        // set formChanged attribute
        if (attrs.get(WorkItem.ATT_FORM_CHANGED) != null) {
            this.setFormChanged(attrs.getBoolean(WorkItem.ATT_FORM_CHANGED));
        }
        if (NativeChangeDetection.FLOW_CONFIG_NAME.equals(attrs.get(IdentityLibrary.VAR_FLOW))) {
            this.setApprovalType(ApprovalType.NativeChange);
        }
        else if (RequestAccessService.FLOW_CONFIG_NAME.equals(attrs.get(IdentityLibrary.VAR_FLOW))) {
            this.setApprovalType(ApprovalType.AccessRequest);
        }

        // set changeSummary
        if (null != attrs.get(WorkItem.ATT_CHANGE_SUMMARY)) {
            this.setChanges((ChangeSummary)attrs.get(WorkItem.ATT_CHANGE_SUMMARY));
        }
        // set summaryName and the type
        if (null != attrs.get(WorkItem.ATT_SUMMARY_NAME)) {
            this.setSummaryName((String)attrs.get(WorkItem.ATT_SUMMARY_NAME));
            this.setApprovalType(ApprovalType.ManageAttribute);
        }
    }

    public void setRoleDifference(BundleDifference roleDiff) {
        this.roleDifference = roleDiff;
    }

    public void setTaskResultId(String id) {
        this.taskResultId = id;
    }

    public void setViewPendingChanges(boolean viewChange) {
        this.viewPendingChanges = viewChange;
    }

    public void setRoleTarget(RoleSummaryDTO target) {
        this.roleTarget = target;
    }

    public void setWorkflowCaseId(String id) {
        this.workflowCaseId = id;
    }

    public void setHasReport(boolean hasReport) {
        this.hasReport = hasReport;
    }

    public boolean isHasForm() {
        return hasForm;
    }

    public void setHasForm(boolean hasForm) {
        this.hasForm = hasForm;
    }

    public boolean isFormChanged() {
        return formChanged;
    }

    public void setFormChanged(boolean isFormChanged) {
        this.formChanged = isFormChanged;
    }
    
    public void setApprovalType(ApprovalType type) {
        this.approvalType = type;
    }

    public String getSummaryName() {
        return summaryName;
    }

    public void setSummaryName(String summaryName) {
        this.summaryName = summaryName;
    }

    public ChangeSummary getChanges() {
        return changes;
    }

    public void setChanges(ChangeSummary changes) {
        this.changes = changes;
    }

    public void setIdentityUpdate(IdentityUpdateDTO identityUpdate) {
        this.identityUpdate = identityUpdate;
    }
}
