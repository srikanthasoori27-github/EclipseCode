/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 *
 * A DTO to represent the WorkItemDTO model.
 * 
 * It gives some methods to the ui tier about the types of items
 * in the WorkItemDTO.  
 *
 */
package sailpoint.web.workitem;

import java.util.Date;
import java.util.List;
import java.util.Map;

import sailpoint.api.Notary;
import sailpoint.api.ObjectUtil;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Identity;
import sailpoint.object.WorkItem;
import sailpoint.service.BaseDTO;
import sailpoint.service.IdentitySummaryDTO;
import sailpoint.service.WorkItemService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.util.WebUtil;


/**
 * A DTO to represent the WorkItemDTO model.
 * 
 * It gives some methods to the ui tier about the types of items
 * in the WorkItemDTO.  
 */
public class WorkItemDTO extends BaseDTO {

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * WorkItem error prefix used to gracefully avoid processing the work item row and column configuration.
     */
    public static final String PREFIX_WORK_ITEM_ERROR_ID = "workItemErrorId";


    private Date created;

    /**
     * The work item type
     */
    private WorkItem.Type workItemType;

    /**
     * The completion state
     */
    private WorkItem.State workItemState;

    /**
     * The name of the work item.
     */
    private String workItemName;

    /**
     * The name (actually the ID) of the identity request.
     */
    private String accessRequestName;

    /**
     * The owner of this work item.
     */
    private IdentitySummaryDTO owner;

    /**
     * The assignee for this work item - only used if the owner is a workgroup.
     */
    private IdentitySummaryDTO assignee;

    /**
     * The identity that made the request that generated this work item.
     */
    private IdentitySummaryDTO requester;

    /**
     * The identity that is the target of this request (aka - requestee).
     */
    private IdentitySummaryDTO target;

    /**
     * The priority of this work item.
     */
    private WorkItem.Level priority;

    /**
     * @The number of comments on this work item.
     */
    private int commentCount;

    /**
     * The electronic signature to display when completing the work item - non-null if an esignature
     * is required.
     */
    private String esigMeaning;

    /**
     *  true if the work item is editable by user context
     */
    private boolean editable;

    /**
     * The work item description.
     */
    private String description;

    /**
     * Date the last notification was sent for this work item
     */
    private Date notificationDate;

    /**
     * Date this work item expires
     */
    private Date expirationDate;

    /**
     * Date that the next reminder will be sent out
     */
    private Date wakeUpDate;

    /**
     * The number of reminder emails that have been sent for this item
     */
    private int reminders;

    /**
     * The number of escalations for this item
     */
    private int escalationCount;

    /**
     * Comments the owner can leave after completing the item.
     */
    private String completionComments;

    /**
     * Used to force the UI to go to the classic work items UI to render custom forms.
     */
    private boolean forceClassicApprovalUI;

    /**
     * Used to navigate to certification details page.
     */
    private String certificationId;

    /**
     * True if this work item can be rendered via the new UI. Value from WorkItemNavigationUtil.isNewTypeWorkItem.
     */
    private boolean newTypeWorkItem;
    
    /**
     * True is forwarding is disabled for workitem, this is used for cert disable delegation forwarding
     */
    private boolean disableForwarding;
    
    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor using a Map approval result.
     *
     * @param  userContext  The UserContext to use.
     * @param  workItem     A map representation of the approval work item.
     * @param  cols         The list of column configs to include in the DTO.
     *
     * @throws GeneralException  If there is a problem loading the work item or the ID is not
     *                           present in the workItem map.
     * @throws ObjectNotFoundException  If a work item with the given ID is not found.
     */
    public WorkItemDTO(UserContext userContext, Map<String,Object> workItem, List<ColumnConfig> cols)
        throws GeneralException, ObjectNotFoundException {

        super(workItem, cols);

        String id = super.getId();
        if (null == id) {
            throw new GeneralException("id is required");
        }

        if (WorkItemDTO.isErrorDTO(this)) {
            // an error as occurred when generating approval result, check if we're able to find workitem id 
            if (id.length() > 14) {
                id = id.substring(14);
            }
            if (null == ObjectUtil.getName(userContext.getContext(), WorkItem.class, id)) {
                return;
            }
        }

        if (null == ObjectUtil.getName(userContext.getContext(), WorkItem.class, id)) {
            throw new ObjectNotFoundException(WorkItem.class, super.getId());
        }

        // Super should setup all of the simple properties ... now build the complex properties.
        // TODO avoid getting the whole work item and use the column config for as much as possible
        initialize(userContext, userContext.getContext().getObjectById(WorkItem.class, id));
    }

    /**
     * Construct a workitem DTO
     * This constructor should only be called by WorkItemDTOFactory!
     *
     * @param  userContext  The UserContext.
     * @param  workItem     The WorkItem for which to create the DTO.
     *
     * @throws GeneralException when fail retrieve current logged in user
     */
    public WorkItemDTO(UserContext userContext, WorkItem workItem) throws GeneralException {
        super(workItem.getId());

        // Copy all the simple properties.
        this.created = workItem.getCreated();
        this.workItemName = workItem.getName();
        this.accessRequestName = workItem.getIdentityRequestId();
        this.priority = workItem.getLevel();
        // Build all of the more complex properties.
        initialize(userContext, workItem);
    }

    /**
     * Initialize complex properties on this object from the given WorkItem.
     *
     * @param  userContext  The UserContext to use.
     * @param  workItem     The WorkItem.
     */
    private void initialize(UserContext userContext, WorkItem workItem) throws GeneralException {
        WorkItemService workItemService = new WorkItemService(workItem, userContext, true);

        // Use work item type from the work item since many column configs don't include this.
        this.workItemType = workItem.getType();
        this.workItemState = workItem.getState();
        this.owner = createIdentitySummary(workItem.getOwner());
        this.assignee = createIdentitySummary(workItem.getAssignee());
        this.requester = createIdentitySummary(workItem.getRequester());
        this.target = workItemService.getTargetIdentitySummary();
        this.commentCount = Util.size(workItem.getComments());
        this.esigMeaning = getEsigMeaning(userContext, workItem);
        this.editable = workItemService.isEditable(userContext.getLoggedInUser());
        this.description = WebUtil.sanitizeHTML(workItem.getDescription());
        this.notificationDate = workItem.getNotification();
        this.completionComments = workItem.getCompletionComments();
        this.expirationDate = workItem.getExpiration();
        this.wakeUpDate = workItem.getWakeUpDate();
        this.reminders = workItem.getReminders();
        this.escalationCount = workItem.getEscalationCount();
        this.disableForwarding = disableDelegationForwarding(workItem, userContext);

        // initialize forceClassicApprovalUI flag
        WorkItemNavigationUtil navigationUtil = new WorkItemNavigationUtil(userContext.getContext());
        this.forceClassicApprovalUI = navigationUtil.forceClassicApprovalUI(workItem);

        this.certificationId = workItem.getCertification();

        this.newTypeWorkItem = navigationUtil.isNewTypeWorkItem(workItem);
    }

    /**
     * return true if the workitem is delegated cert and delegation forwarding is disabled
     * @param {WorkItem} workitem
     * @return true is delegation forwarding is disabled for delegation workitem
     * @throws GeneralException
     */
    private boolean disableDelegationForwarding(WorkItem workItem, UserContext userContext) throws GeneralException{
        WorkItem.Type workItemType = workItem.getType();
        if (workItem.getCertification() != null && WorkItem.Type.Delegation.equals(workItemType)) {
           return workItem.getCertification(userContext.getContext()).getCertificationDefinition(userContext.getContext()).isDelegationForwardingDisabled();
        }
        return false;
    }

    /**
     * Load the electronic signature meaning for this work item.
     */
    private String getEsigMeaning(UserContext userContext, WorkItem workItem)
        throws GeneralException {

        Notary notary = new Notary(userContext.getContext(), userContext.getLocale());
        return notary.getSignatureMeaning(WorkItem.class, workItem.getAttributes());
    }

    /**
     * Return an IdentitySummary for the given identity or null if the identity is null.
     */
    protected static IdentitySummaryDTO createIdentitySummary(Identity identity) {
        return (null != identity) ? new IdentitySummaryDTO(identity) : null;
    }
    
    /**
     * Checks if the workItemDTO id starts with {@link #PREFIX_WORK_ITEM_ERROR_ID}.
     * If so this is considered an error type of WorkItemDTO
     * @param workItemDTO the workItemDTO, can be null
     * @return true if the id is not null and starts with {@link #PREFIX_WORK_ITEM_ERROR_ID}
     */
    public static boolean isErrorDTO(WorkItemDTO workItemDTO) {
        return workItemDTO != null && workItemDTO.getId() != null && workItemDTO.getId().startsWith(PREFIX_WORK_ITEM_ERROR_ID);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // BASIC PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public WorkItem.Type getWorkItemType() {
        return workItemType;
    }

    public void setWorkItemType(WorkItem.Type workItemType) {
        this.workItemType = workItemType;
    }

    public WorkItem.State getWorkItemState() {
        return workItemState;
    }

    public void setWorkItemState(WorkItem.State workItemState) {
        this.workItemState = workItemState;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public String getWorkItemName() {
        return workItemName;
    }

    public void setWorkItemName(String workItemName) {
        this.workItemName = workItemName;
    }

    public String getAccessRequestName() {
        return accessRequestName;
    }

    public void setAccessRequestName(String accessRequestName) {
        this.accessRequestName = accessRequestName;
    }

    public WorkItem.Level getPriority() {
        return priority;
    }

    public void setPriority(WorkItem.Level priority) {
        this.priority = priority;
    }

    public void setCommentCount(int commentCount) {
        this.commentCount = commentCount;
    }

    public Date getNotificationDate() {
        return notificationDate;
    }

    public void setNotificationDate(Date notificationDate) {
        this.notificationDate = notificationDate;
    }

    public Date getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(Date expirationDate) {
        this.expirationDate = expirationDate;
    }

    public Date getWakeUpDate() {
        return wakeUpDate;
    }

    public void setWakeUpDate(Date wakeUpDate) {
        this.wakeUpDate = wakeUpDate;
    }

    public int getReminders() {
        return reminders;
    }

    public void setReminders(int reminders) {
        this.reminders = reminders;
    }

    public int getEscalationCount() {
        return escalationCount;
    }

    public void setEscalationCount(int escalationCount) {
        this.escalationCount = escalationCount;
    }

    public String getCompletionComments() {
        return completionComments;
    }

    public void setCompletionComments(String completionComments) {
        this.completionComments = completionComments;
    }

    public boolean isForceClassicApprovalUI() {
        return forceClassicApprovalUI;
    }

    public void setForceClassicApprovalUI(boolean forceClassicApprovalUI) {
        this.forceClassicApprovalUI = forceClassicApprovalUI;
    }

    public String getCertificationId() {
        return certificationId;
    }

    public void setCertificationId(String certificationId) {
        this.certificationId = certificationId;
    }

    public boolean isNewTypeWorkItem() {
        return newTypeWorkItem;
    }

    public void setNewTypeWorkItem(boolean newTypeWorkItem) {
        this.newTypeWorkItem = newTypeWorkItem;
    } 

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // CALCULATED PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public IdentitySummaryDTO getOwner() {
        return owner;
    }

    public IdentitySummaryDTO getAssignee() {
        return assignee;
    }

    public IdentitySummaryDTO getRequester() {
        return requester;
    }

    public IdentitySummaryDTO getTarget() {
        return target;
    }

    public int getCommentCount() {
        return commentCount;
    }

    public String getEsigMeaning() {
        return esigMeaning;
    }
   
    /**
     * @return true if editable
     */
    public boolean isEditable() {
        return editable;
    }

    /**
     * Gets the work item description.
     *
     * @return The description.
     */
    public String getDescription() {
        return description;
    }

    public boolean isDisableForwarding() {
        return disableForwarding;
    }
}
