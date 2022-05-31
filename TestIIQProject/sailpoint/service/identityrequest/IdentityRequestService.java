package sailpoint.service.identityrequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.SailPointContext;
import sailpoint.api.Workflower;
import sailpoint.authorization.WorkItemArchiveAuthorizer;
import sailpoint.authorization.WorkItemAuthorizer;
import sailpoint.authorization.identityrequest.IdentityRequestCancelAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.integration.RequestResult;
import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalSet;
import sailpoint.object.AuditEvent;
import sailpoint.object.Comment;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleAssignment;
import sailpoint.object.Source;
import sailpoint.object.TaskResult;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkItem.State;
import sailpoint.object.WorkItemArchive;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowSummary;
import sailpoint.object.WorkflowSummary.ApprovalSummary;
import sailpoint.server.Auditor;
import sailpoint.service.ApprovalItemsService;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.LCMConfigService;
import sailpoint.service.LinkService;
import sailpoint.service.MessageDTO;
import sailpoint.service.ProvisioningTransactionService;
import sailpoint.service.SimpleListServiceContext;
import sailpoint.service.identity.TargetAccountDTO;
import sailpoint.service.pam.PamApprovalService;
import sailpoint.service.pam.PamRequest;
import sailpoint.service.pam.PamRequestService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.Sorter;
import sailpoint.web.util.WebUtil;

/**
 * Service for interacting with IdentityRequests and their IdentityRequestItems
 */
public class IdentityRequestService {

    /**
     * ListServiceContext to use for listing items. Limits to only top-level requested item.
     */
    private static class ItemListServiceContext extends SimpleListServiceContext implements BaseListServiceContext {
        ItemListServiceContext (UserContext userContext) {
            super(userContext);
        }

        @Override
        public List<Filter> getFilters() {
            List<Filter> filters = new ArrayList<>();
            filters.add(Filter.isnull("compilationStatus"));
            return filters;
        }
    }

    // Sortable column config properties
    private final String OWNER = "ownerDisplayName";
    private final String OPEN_DATE= "openDate";
    private final String COMPLETION_DATE= "completeDate";
    private final String DESCRIPTION = "description";
    // Sort direction
    private final String DESC = "DESC";

    // messages sorting
    private final String TYPE = "type";
    private final String MESSAGE = "message";

    private static Log log = LogFactory.getLog(IdentityRequestService.class);
    
    private IdentityRequest identityRequest;
    private SailPointContext context;

    /**
     * Constructor
     * @param context SailPointContext
     * @throws InvalidParameterException
     */
    public IdentityRequestService(SailPointContext context, IdentityRequest identityRequest) throws InvalidParameterException {
        if (context == null) {
            throw new InvalidParameterException("context");
        }
        
        if (identityRequest == null) {
            throw new InvalidParameterException("identityRequest");
        }

        this.context = context;
        this.identityRequest = identityRequest;
    }

    public IdentityRequestDTO getDto(UserContext userContext) throws GeneralException {
        IdentityRequestDTO dto = new IdentityRequestDTO(this.identityRequest);
        if (dto.getCompletionStatus() == null && dto.getEndDate() == null) {
            dto.setCompletionStatus(IdentityRequest.CompletionStatus.Pending);
        }

        // only set external ticket id if it is configured
        if (new LCMConfigService(this.context).isShowExternalTicketId()) {
            dto.setExternalTicketId(this.identityRequest.getExternalTicketId());
        }

        IdentityRequestItemListService itemListService =
                new IdentityRequestItemListService(userContext, new ItemListServiceContext(userContext), new IdentityRequestItemListService.IdentityRequestItemListColumnSelector(null));
        ListResult itemsListResult = itemListService.getIdentityRequestItems(this.identityRequest, true);
        dto.setItems(itemsListResult.getObjects());
        dto.setCancelable(isRequestCancelable(this.identityRequest, userContext));
        dto.setInteractions(getInteractionDTOs());
        updateApprovalInfo(this.identityRequest, dto, userContext);
        return dto;
    }

    /**
     * Get the interactions for the identity request. We refer to the work items as approval summaries but they actually
     * include non approval work items as well. This is used to get interaction information to identity request card list
     * @return {List<ApprovalSummaryDTO>} list of interactions
     * @throws GeneralException
     */
    private List<ApprovalSummaryDTO> getInteractionDTOs () throws GeneralException {
        List<ApprovalSummaryDTO> interactionDTOs = new ArrayList<ApprovalSummaryDTO> ();
        List<ApprovalSummary> interactions = this.getApprovalSummaryList();

        // return empty list if no interactions
        if (Util.isEmpty(interactions)) {
            return interactionDTOs;
        }

        this.checkApprovalSummaries(interactions);
        
        for (ApprovalSummary interaction : interactions) {
            interactionDTOs.add(new ApprovalSummaryDTO(interaction));
        }
        return interactionDTOs;
    }

    /**
     * Get the identity request messages. Error, warning and info type messages are included.
     *
     * @param userContext the user context
     * @param sortBy sort by data string
     * @param start start index
     * @param limit result limit
     * @return ListResult list result of messages
     */
    public ListResult getMessages(UserContext userContext, String sortBy, int start, int limit) throws GeneralException {
        List<Message> messages = this.identityRequest.getMessages();

        // return empty list result if no messages
        if (Util.isEmpty(messages)) {
            return ListResult.getInstance();
        }

        // total message count
        int messageCount = messages.size();

        // sort messages
        if (!Util.isNullOrEmpty(sortBy)) {
            // deserialize into sorter object
            List<Sorter> sorts = JsonHelper.listFromJson(Sorter.class, sortBy);
            if (!sorts.isEmpty()) {
                Sorter sort = sorts.get(0);
                String sortDirection = sort.getDirection();
                String sortProperty = sort.getProperty();

                if (TYPE.equals(sortProperty)) {
                    Collections.sort(messages, new MessageTypeComparator());
                }
                else if (MESSAGE.equals(sortProperty)) {
                    Collections.sort(messages, new MessageTextComparator());
                }

                if (DESC.equalsIgnoreCase(sortDirection)) {
                    Collections.reverse(messages);
                }
            }
        }
        else {
            // default sort by type
            Collections.sort(messages, new MessageTypeComparator());
        }

        // take care of paging
        if (start != 0 || limit != 0) {
            int end = start + limit;
            if (end > messages.size()) {
                end = messages.size();
            }

            return new ListResult(convertToMessageDTOs(userContext, messages.subList(start, end)), messageCount);
        }

        return new ListResult(convertToMessageDTOs(userContext, messages), messageCount);
    }

    /**
     * Convert message list to message dto list and translate message keys
     *
     * @param userContext user context
     * @param messages list of messages to convert
     * @return List<MessageDTO> list of message dtos
     */
    private List<MessageDTO> convertToMessageDTOs(UserContext userContext, List<Message> messages) {
        List<MessageDTO> messageDTOs = new ArrayList<>();

        // convert to message dtos
        for (Message message : Util.safeIterable(messages)) {
            // translate messages
            MessageDTO messageDTO = new MessageDTO(message);

            String translatedMessage = message.getLocalizedMessage(userContext.getLocale(), userContext.getUserTimeZone());
            messageDTO.setMessageOrKey(translatedMessage);

            messageDTOs.add(messageDTO);
        }

        return messageDTOs;
    }

    /**
     * Check if the request is cancelable
     * @param request IdentityRequest object
     * @param userContext UserContext to base decision on
     * @return True if request can be canceled by logged in user, otherwise false
     * @throws GeneralException
     */
    private boolean isRequestCancelable(IdentityRequest request, UserContext userContext) throws GeneralException {
        // Can cancel if request is not complete (endDate not set) and not already Terminated
        return (request.getEndDate() == null &&
                !IdentityRequest.ExecutionStatus.Terminated.equals(request.getExecutionStatus()) &&
                IdentityRequestCancelAuthorizer.isAuthorized(request, userContext));
    }

    /**
     * Check whether the user can access comments.
     * User must have authorization for work item to be able to access comments. Allow requester access.
     *
     * @return boolean true if user can access comments
     */
    private boolean getCanAccessComments(WorkItem workItem, UserContext userContext) throws GeneralException {
        return WorkItemAuthorizer.isAuthorized(workItem, userContext, true);
    }

    /**
     * Correlate the approval work items associated with the request to their items
     * @param request IdentityRequest object
     * @param dto IdentityRequestDTO object
     * @param userContext used to determine if user can access comment
     * @throws GeneralException
     */
    private void updateApprovalInfo(IdentityRequest request, IdentityRequestDTO dto, UserContext userContext) throws GeneralException {
        QueryOptions ops = new QueryOptions(
                Filter.eq("identityRequestId", request.getName()),
                Filter.eq("type", WorkItem.Type.Approval));
        IncrementalObjectIterator<WorkItem> approvalIterator = new IncrementalObjectIterator<>(this.context, WorkItem.class, ops);
        while (approvalIterator.hasNext()) {
            WorkItem workItem = approvalIterator.next();
            ApprovalSet approvalSet = workItem.getApprovalSet();
            boolean isPam = false;
            // Special Case, PAM: Look for a PamRequest. If we have one, convert it to an ApprovalSet to match
            // items, also disable comments for PAM items
            if (approvalSet == null) {
                PamRequest pamRequest = (PamRequest) workItem.get(PamApprovalService.ARG_PAM_REQUEST);
                if (pamRequest != null) {
                    PamRequestService pamRequestService = new PamRequestService(this.context);
                    approvalSet = pamRequestService.createApprovalSet(pamRequest);
                    isPam = true;
                }
            }

            if (approvalSet != null) {
                for (ApprovalItem approvalItem : Util.iterate(approvalSet.getItems())) {
                    ApprovalItemsService itemService = new ApprovalItemsService(this.context, approvalItem);
                    int commentCount = itemService.getCommentCount();
                    for (IdentityRequestItem identityRequestItem : Util.iterate(request.findItems(approvalItem))) {

                        IdentityRequestItemDTO itemDTO = dto.getItemDTO(identityRequestItem.getId());
                        if (itemDTO != null) {

                            IdentityRequestItemDTO.ApprovalItemSummary approvalItemSummary =
                                    new IdentityRequestItemDTO.ApprovalItemSummary(workItem, approvalItem.getId(), commentCount);
                            if (isPam) {
                                // For PAM requests, approval item id is bogus (since we made a fake to match) and there are never comments
                                approvalItemSummary.setApprovalItemId(null);
                                approvalItemSummary.setCanAccessComments(false);
                                approvalItemSummary.setCommentCount(0);
                            } else {
                                approvalItemSummary.setCanAccessComments(getCanAccessComments(workItem, userContext));
                            }
                            itemDTO.addApprovalItemSummary(approvalItemSummary);
                        }
                    }
                }
            }
        }
    }

    /**
     * Get the target account information for an ApprovalItem
     * @param approvalItem ApprovalItem to match to an IdentityRequestItem
     * @param targetIdentity Target Identity
     * @return List of TargetAccounts
     * @throws GeneralException
     */
    public List<TargetAccountDTO> getTargetAccounts(ApprovalItem approvalItem, Identity targetIdentity) throws GeneralException {
      return getTargetAccounts(findIdentityRequestItem(approvalItem), targetIdentity);  
    }
    
    /**
     * Get the target account information for an IdentityRequestItem
     * @param item IdentityRequestItem
     * @param targetIdentity Target Identity
     * @return List of TargetAccounts 
     * @throws GeneralException
     */
    public List<TargetAccountDTO> getTargetAccounts(IdentityRequestItem item, Identity targetIdentity) throws GeneralException {
        List<TargetAccountDTO> accounts = new ArrayList<TargetAccountDTO>();
        // Only assignedRoles should show target accounts
        if (item != null && ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES.equals(item.getName())) {
            LinkService linksService = new LinkService(this.context);
            ProvisioningPlan plan = item.getProvisioningPlan();
           
            for (ProvisioningPlan.AccountRequest areq : plan.getAccountRequests()) {
                if (!ProvisioningPlan.IIQ_APPLICATION_NAME.equalsIgnoreCase(areq.getApplicationName())) {
                    IdentityRequestItemService itemService = new IdentityRequestItemService(this.context, item);
                    TargetAccountDTO account = new TargetAccountDTO();
                    account.setValue(itemService.getDisplayableValue());
                    account.setApplication(areq.getApplication());
                    account.setNativeIdentity(areq.getNativeIdentity());
                    account.setAccount(linksService.getAccountDisplayName(targetIdentity, areq.getApplication(), areq.getInstance(), areq.getNativeIdentity()));
                    account.setSourceRole(areq.getSourceRole());
                    account.setInstance(areq.getInstance());
                    accounts.add(account);
                }
            }
        }

        return accounts;
    }

    /**
     * Get the interactions for the identity request. We refer to the work items as approval summaries but they actually
     * include non approval work items as well.
     *
     * @param userContext UserContext
     * @param sortBy sort by string
     * @param start start index
     * @param limit limit per page
     * @return ListResult list of approval summary DTOs
     * @throws GeneralException
     */
    public ListResult getInteractions(UserContext userContext, String sortBy, int start, int limit) throws GeneralException {
        List<ApprovalSummaryDTO> approvalSummaryDTOList = new ArrayList<>();

        List<ApprovalSummary> approvalSummaries = this.getApprovalSummaryList();

        // return empty list result if no interactions
        if (Util.isEmpty(approvalSummaries)) {
            return ListResult.getInstance();
        }

        this.checkApprovalSummaries(approvalSummaries);

        int count = approvalSummaries.size();

        // handle sorting before paging
        sortApprovalSummaries(approvalSummaries, sortBy);

        // handle paging
        Iterator<ApprovalSummary> approvalSummaryIterator = approvalSummaries.iterator();

        if (start > 0) {
            // fast forward
            for (int i = 0; i < start; ++i) {
                if (approvalSummaryIterator.hasNext()) {
                    approvalSummaryIterator.next();
                }
                else {
                    break;
                }
            }
        }

        while (approvalSummaryIterator.hasNext()) {
            ApprovalSummary summary = approvalSummaryIterator.next();
            ApprovalSummaryDTO dto = new ApprovalSummaryDTO(summary);
            setWorkItemFields(dto, userContext);
            approvalSummaryDTOList.add(dto);
            if (limit > 0 && approvalSummaryDTOList.size() >= limit) {
                Util.flushIterator(approvalSummaryIterator);
                break;
            }
        }

        return new ListResult(approvalSummaryDTOList, count);
    }

    /**
     * Set the work item related fields on the ApprovalSummaryDTO based on work item existence and authorization.
     */
    private void setWorkItemFields(ApprovalSummaryDTO dto, UserContext userContext) throws GeneralException {
        if (Util.isNothing(dto.getWorkItemId())) {
            return;
        }

        WorkItem workItem = this.context.getObjectById(WorkItem.class, dto.getWorkItemId());
        if (workItem != null) {
            dto.setHasWorkItemAccess(WorkItemAuthorizer.isAuthorized(workItem, userContext, true));
            dto.setWorkItemName(workItem.getName());
        } else {
            QueryOptions options = new QueryOptions();
            options.add(Filter.eq("workItemId", dto.getWorkItemId()));
            List<WorkItemArchive> workItemArchives = this.context.getObjects(WorkItemArchive.class, options);
            if (!Util.isEmpty(workItemArchives)) {
                // should only be 1 archive per work item, duh
                WorkItemArchive archive = workItemArchives.get(0);
                dto.setWorkItemArchiveId(archive.getId());
                dto.setWorkItemName(archive.getName());
                dto.setHasWorkItemAccess(WorkItemArchiveAuthorizer.isAuthorized(archive, userContext));
            }
        }
    }

    /**
     * Depending on the status of the identity request get the list of approval summaries from the workflow or directly
     * from the identity request object.
     *
     * @return List<ApprovalSummary> list of ApprovalSummary objects
     * @throws GeneralException
     */
    public List<ApprovalSummary> getApprovalSummaryList() throws GeneralException {
        List<ApprovalSummary> approvalSummaries;

        // While IdentityRequest is still executing, we need the ApprovalSummary data from WorkflowSummary interactions.
        // Workflow interactions include pending approvals, so no need to call the getPendingApprovals() method here.
        if (this.identityRequest.isExecuting()) {
            approvalSummaries = this.getWorkflowInteractions();
        }
        else {
            approvalSummaries = this.identityRequest.getApprovalSummaries();
        }

        // make sure list is initialized
        if (approvalSummaries == null) {
            approvalSummaries = new ArrayList<>();
        }

        return approvalSummaries;
    }

    /**
     * Sort the approval summaries. We are only sorting by owner, open date, and completion date.
     * We only handle the new sorters that look like: [{"property":"rule", "direction": "ASC"}].
     * We don't do secondary sorts so only need to use the first sorter.
     *
     * @param approvalSummaries list of approval summaries to sort
     * @param sortBy sort by property and direction
     * @throws GeneralException
     */
    private void sortApprovalSummaries(List<ApprovalSummary> approvalSummaries, String sortBy) throws GeneralException {
        List<Sorter> sorts;

        if (!Util.isNullOrEmpty(sortBy)) {
            // deserialize into sorter object
            sorts = JsonHelper.listFromJson(Sorter.class, sortBy);
            if (!sorts.isEmpty()) {
                Sorter sort = sorts.get(0);
                String sortDirection = sort.getDirection();
                String sortProperty = sort.getProperty();

                if (OWNER.equals(sortProperty)) {
                    Collections.sort(approvalSummaries, new ApprovalSummaryOwnerComparator());
                }
                else if (OPEN_DATE.equals(sortProperty)) {
                    Collections.sort(approvalSummaries, new ApprovalSummaryOpenDateComparator());
                }
                else if (COMPLETION_DATE.equals(sortProperty)) {
                    Collections.sort(approvalSummaries, new ApprovalSummaryCompletionDateComparator());
                }
                else if (DESCRIPTION.equals(sortProperty)) {
                    Collections.sort(approvalSummaries, new ApprovalSummaryDescriptionComparator());
                }

                if (DESC.equalsIgnoreCase(sortDirection)) {
                    Collections.reverse(approvalSummaries);
                }
            }
        }
    }

    /**
     * Check to see if approval summaries still exist.
     * If the work item id can't be found in workitem or workitemarchive table set the id to null.
     * Also check and make sure owner info is correct.
     *
     * @param approvalSummaries list of approval summary objects
     */
    public void checkApprovalSummaries(List<ApprovalSummary> approvalSummaries) throws GeneralException {
        Iterator<ApprovalSummary> approvalSummaryIterator = approvalSummaries.iterator();
        while(approvalSummaryIterator.hasNext()) {
            ApprovalSummary approvalSummary = approvalSummaryIterator.next();

            String workItemId = approvalSummary.getWorkItemId();
            WorkItem workItem = null;
            if (Util.isNotNullOrEmpty(workItemId)) {
               workItem = this.context.getObjectById(WorkItem.class, workItemId);
            }
            if (workItem == null) {
                // try workitemarchive
                QueryOptions wiaop = new QueryOptions();
                wiaop.add(Filter.eq("workItemId", workItemId));
                if (this.context.countObjects(WorkItemArchive.class, wiaop) == 0) {
                    // no workitem or workitemarchive found so set the work item id to null
                    approvalSummary.setWorkItemId(null);
                }
            }
            else {
                // work item found. need to set owner data in case of a forwarded work item while it's still open.
                if (workItem.getOwner() != null) {
                    approvalSummary.setOwner(workItem.getOwner().getDisplayableName());
                    approvalSummary.setOwnerId(workItem.getOwner().getId());
                }
            }

            // use the displayable name
            if (approvalSummary.getOwner() != null) {
                Identity owner = this.context.getObjectByName(Identity.class, approvalSummary.getOwner());
                if (owner != null) {
                    approvalSummary.setOwnerId(owner.getId());
                    approvalSummary.setOwner(owner.getDisplayableName());
                }
            }
        }
    }

    /**
     * Not all ApprovalSummary data is written to the IdentityRequest until
     * the "Complete Identity Request" step of the "Identity Request Finalize" workflow.
     * Until that time, we must get all the ApprovalSummary data from the respective TaskResult.
     * The TaskResult data can be trusted since it will be updated as each ApprovalSummary is updated.
     * This method retrieves the ApprovalSummary data from the TaskResult.
     *
     * @return List<ApprovalSummary> list of approval summary objects
     * @throws GeneralException
     */
    public List<ApprovalSummary> getWorkflowInteractions() throws GeneralException {
        List<ApprovalSummary> workflowInteractions = null;

        TaskResult taskResult = this.context.getObjectById(TaskResult.class,  this.identityRequest.getTaskResultId());

        if (taskResult != null) {
            WorkflowSummary workflowSummary = (WorkflowSummary)taskResult.getAttribute(WorkflowCase.RES_WORKFLOW_SUMMARY);
            if (workflowSummary != null) {
                workflowInteractions = workflowSummary.getInteractions();
            }
        }

        return workflowInteractions;
    }

    /**
     * Find an IdentityRequestItem for the given approval item
     * @param approvalItem ApprovalItem
     * @return IdentityRequestItem
     */
    public IdentityRequestItem findIdentityRequestItem(ApprovalItem approvalItem) {
        if (approvalItem != null) {
            String assignment = approvalItem.getAssignmentId();
            String val = null;
            if (approvalItem.getValue() instanceof String && !Util.isAnyNullOrEmpty((String)approvalItem.getValue())) {
                val = (String) approvalItem.getValue();
            /*
             * bug25596 : Xerox / Novell added a list of values for custom processing and that's
             * causing trouble in the mobile ui. We don't expect to have an identity request in the values list.
             */
            } else if (null != approvalItem.getValue() && approvalItem.getValue() instanceof List){
                log.warn("List of values are not supported as arguments, a String value is the expected.");
                return null;
            }
            return findIdentityRequestItemByAssignmentIdAndValue(assignment, val);
        }

        return null;
    }

    /**
     * Find an IdentityRequestItem by matching an ID
     * @param id IdentityRequestItem ID to match
     * @return IdentityRequestItem, or null if it does not exist
     */
    public IdentityRequestItem findIdentityRequestItemById(String id) {
        for (IdentityRequestItem item: identityRequest.getItems()) {
            if (Util.nullSafeEq(id, item.getId())) {
                return item;
            }
        }

        return null;
    }

    /**
     * Find an IdentityRequestItem by matching a value
     * @param value String value to match
     * @return IdentityRequestItem, or null if no match exists
     */
    public IdentityRequestItem findIdentityRequestItemByValue(String value) {
        return findIdentityRequestItemByAssignmentIdAndValue(null, value);
    }
   
    /**
     * Get the assignment note information for an ApprovalItem
     * @param approvalItem ApprovalItem for assignment note
     * @param targetIdentity the identity with the assignment note only required if operation is "Remove"
     * @return assignment note 
     * @throws GeneralException
     */
    public String getAssignmentNote(ApprovalItem approvalItem, Identity targetIdentity) throws GeneralException {
        if (approvalItem == null) {
            throw new InvalidParameterException("approvalItem");
        }
        //if this is a role removal, the assignment note is part of the Identity rather than on the request so it needs
        //to be found a different way
        String operation = approvalItem.getOperation();
        if (Util.nullSafeEq(ProvisioningPlan.Operation.Remove.toString(), operation)) {
            String assignmentId = approvalItem.getAssignmentId();
            RoleAssignment assignment = targetIdentity.getRoleAssignmentById(assignmentId);
            if (null != assignment) {
                return assignment.getComments();
            } else {
                return null;
            }
        } else {
            return getAssignmentNote(findIdentityRequestItem(approvalItem), approvalItem.getAssignmentId());
        }
    }

    /**
     * Get the assignment note information for an IdentityRequestItem
     * @param item IdentityRequestItem
     * @param roleAssignmentId roleAssignmentId
     * @return assignment note
     * @throws GeneralException
     */
    public String getAssignmentNote(IdentityRequestItem item, String roleAssignmentId) throws GeneralException {
        if (item != null && item.getProvisioningPlan() != null) {
            ProvisioningPlan plan = item.getProvisioningPlan();
            for (ProvisioningPlan.AccountRequest areq : Util.safeIterable(plan.getAccountRequests())) {
                if (ProvisioningPlan.IIQ_APPLICATION_NAME.equalsIgnoreCase(areq.getApplicationName())) {
                    List<AttributeRequest> attreqs = areq.getAttributeRequests();
                    for (AttributeRequest req : attreqs) {
                        if (!Util.isEmpty(roleAssignmentId)) {
                            if (Util.nullSafeEq(roleAssignmentId, req.getAssignmentId())) {
                                return req.getString(ProvisioningPlan.ARG_ASSIGNMENT_NOTE);
                            }
                        } else {
                            //return the note on the first attribute request if assignmentId is null
                            return req.getString(ProvisioningPlan.ARG_ASSIGNMENT_NOTE);
                        }
                    }
                }
            }
        }

        return null;
    }


    /**
     * Finds the IdentityRequestItem based on assignmentId and roleName.
     * If assignmentId is null, only checks for the roleName.
     * @param roleAssignmentId  The assignmentId that matches the one in IdentityRequestItem
     * @param roleName  The role name that matches the IdentityRequestItem value
     * @return
     */
    public IdentityRequestItem findIdentityRequestItemByAssignmentIdAndValue(String roleAssignmentId,
                                                              String roleName) {
        for (IdentityRequestItem item: identityRequest.getItems()) {
            if (Util.nullSafeEq(roleName, item.getValue())) {
                if (Util.isNullOrEmpty(roleAssignmentId)) {
                    //if roleAssignmentId passed in is null, then just check for the roleName
                    return item;
                } else {
                    ProvisioningPlan plan = item.getProvisioningPlan();
                    for (ProvisioningPlan.AccountRequest areq : Util.safeIterable(plan.getAccountRequests())) {
                        if (ProvisioningPlan.IIQ_APPLICATION_NAME.equalsIgnoreCase(areq.getApplicationName())) {
                            List<AttributeRequest> attreqs = areq.getAttributeRequests();
                            for (AttributeRequest req : attreqs) {
                                if (Util.nullSafeEq(roleAssignmentId, req.getAssignmentId())) {
                                    return item;
                                }
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Cancel the request along with the workflow and associated task result
     * @param userContext UserContext
     * @param comments Optional comments for canceling
     * @return RequestResult with status and any error message
     * @throws GeneralException
     */
    public RequestResult cancelWorkflow(UserContext userContext, String comments) throws GeneralException {
        if (userContext == null) {
            throw new InvalidParameterException("userContext");
        }

        RequestResult result = new RequestResult();

        TaskResult task = null;
        String taskResultId = this.identityRequest.getTaskResultId();
        if (taskResultId != null) {
            task = this.context.getObjectById(TaskResult.class, taskResultId);
        }

        WorkflowCase wfCase = null;
        if (task != null) {
            String caseId = (String) task.getAttribute(WorkflowCase.RES_WORKFLOW_CASE);
            if (caseId != null) {
                wfCase = this.context.getObjectById(WorkflowCase.class, caseId);
            }
        }

        // If we cant find a task result or a running workflow case, bail with an error.
        if (task == null || (task.getCompleted() == null && wfCase == null)) {
            result.setStatus(RequestResult.STATUS_FAILURE);
            result.addError(Message.error(MessageKeys.TASK_RESULT_WORKFLOW_NOT_FOUND, taskResultId).getLocalizedMessage(userContext.getLocale(), userContext.getUserTimeZone()));
            return result;
        }

        // We only need to cancel if task is not already completed
        if (task.getCompleted() == null) {
            Date terminatedDate = new Date();
            Workflower workflower = new Workflower(this.context);
            workflower.terminate(wfCase);

            task.setTerminated(true);
            task.setCompleted(terminatedDate);
            task.setCompletionStatus(task.calculateCompletionStatus());
            this.context.saveObject(task);

            failProvisioningTransactions(this.identityRequest, this.context);

            // the workflower terminate() method causes lazy init problems
            // on the identity request if we don't refresh the request here.
            // However, the workflower needs to run first so that it can
            // finish its job of creating an approval summary.  Ah, Hibernate...
            this.identityRequest = this.context.getObjectById(IdentityRequest.class, this.identityRequest.getId());
            this.identityRequest.setExecutionStatus(IdentityRequest.ExecutionStatus.Terminated);
            this.identityRequest.setAttribute(IdentityRequest.ATT_TERMINATED_DATE, terminatedDate);

            // Add a message with the comment
            Comment comment = null;
            if (!Util.isNothing(comments)) {
                comment = new Comment();
                //IIQETN-4897 :- Escaping "comments" field to avoid XSS vulnerability
                comments = WebUtil.escapeHTML(WebUtil.safeHTML(comments), false);
                comment.setComment(new Message(MessageKeys.COMMENT_TERMINATED_PREFIX).getLocalizedMessage() + " " + comments);
                comment.setDate(new Date());
                comment.setAuthor(userContext.getLoggedInUser().getDisplayableName());
                this.identityRequest.addMessage(new Message(Message.Type.Info, comment.toString()));

                for (WorkflowSummary.ApprovalSummary summary : Util.iterate(this.identityRequest.getApprovalSummaries())) {
                    summary.addComment(comment);
                }
            }

            // Cancel any items pending approval
            List<IdentityRequestItem> items = this.identityRequest.getItems();
            for (IdentityRequestItem item : Util.iterate(items)) {
                if (item != null) {
                    WorkItem.State state = item.getApprovalState();
                    if (state == null || state == WorkItem.State.Pending) {
                        item.setApprovalState(WorkItem.State.Canceled);
                    }
                }
            }

            this.context.saveObject(this.identityRequest);
            this.context.commitTransaction();

            // Audit the cancelation
            AuditEvent event = new AuditEvent();
            event.setSource(userContext.getLoggedInUserName());
            event.setTarget(task.getTargetName());
            event.setTrackingId(wfCase.getWorkflow().getProcessLogId());
            event.setAction(AuditEvent.CancelWorkflow);
            // djs: for now set this in both places to avoid needing
            // to upgrade.  Once we have ui support for "interface"
            // we can remove the map version
            event.setAttribute("interface", Source.LCM.toString());
            event.setInterface(Source.LCM.toString());
            event.setAttribute(Workflow.VAR_TASK_RESULT, taskResultId);
            if (comment != null) {
                // Storing as a list so we're consistent with other audit event types.
                event.setAttribute("completionComments", Collections.singletonList(comment));
            }
            Auditor.log(event);
            this.context.commitTransaction();

            // Get the latest identity request
            this.identityRequest = this.context.getObjectById(IdentityRequest.class, this.identityRequest.getId());
        }

        result.setStatus(RequestResult.STATUS_SUCCESS);
        return result;
    }

    /**
     * Use the ProvisioningTransaction service to fail any provisioning transactions
     * based on the abstract requests in the Identity Request's provisioned project.
     * This will mark the associated Provisioning Transactions to be in a Failed status.
     */
    private void failProvisioningTransactions(IdentityRequest request, SailPointContext context) throws GeneralException {
        if (request != null && request.getProvisionedProject() != null) {
            ProvisioningTransactionService pts = new ProvisioningTransactionService(context);
            List<ProvisioningPlan> plans = request.getProvisionedProject().getPlans();

            for (ProvisioningPlan plan : Util.iterate(plans)) {
                List<ProvisioningPlan.AbstractRequest> allRequests = plan.getAllRequests();

                for (ProvisioningPlan.AbstractRequest abstractRequest : Util.iterate(allRequests)) {
                    pts.failTransaction(abstractRequest);
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Identity request item comparator methods
    //
    ////////////////////////////////////////////////////////////////////////////
    public static class MessageTypeComparator implements Comparator<Message> {
        public int compare(Message message1, Message message2) {
            String type1 = (message1 == null || message1.getType() == null) ? null : message1.getType().name();
            String type2 = (message2 == null || message2.getType() == null) ? null : message2.getType().name();

            return Util.nullSafeCompareTo(type1, type2);
        }
    }

    public static class MessageTextComparator implements Comparator<Message> {
        public int compare(Message message1, Message message2) {
            String stringMsg1 = (message1 == null) ? null : message1.getKey();
            String stringMsg2 = (message2 == null) ? null : message2.getKey();

            return Util.nullSafeCompareTo(stringMsg1, stringMsg2);
        }
    }

    public static class ApprovalSummaryDescriptionComparator implements Comparator<ApprovalSummary> {

        public int compare(ApprovalSummary o1, ApprovalSummary o2) {

            String desc1 = o1.getRequest();
            String desc2 = o2.getRequest();
            if (desc1 == null || desc2 == null) {
                return 0;
            }
            return desc1.compareTo(desc2);
        }
    }

    public static class ApprovalSummaryOwnerComparator implements Comparator<ApprovalSummary> {

        public int compare(ApprovalSummary o1, ApprovalSummary o2) {

            String owner1 = o1.getOwner();
            String owner2 = o2.getOwner();
            if (owner1 == null || owner2 == null) {
                return 0;
            }
            return owner1.compareTo(owner2);
        }
    }

    public static class ApprovalSummaryOpenDateComparator implements Comparator<ApprovalSummary> {

        public int compare(ApprovalSummary o1, ApprovalSummary o2) {
            Date date1 = o1.getStartDate();
            Date date2 = o2.getStartDate();
            return new NullsFirstDateComparator().compare(date1, date2);
        }
    }

    public static class ApprovalSummaryCompletionDateComparator implements Comparator<ApprovalSummary> {

        public int compare(ApprovalSummary o1, ApprovalSummary o2) {
            Date date1 = o1.getEndDate();
            Date date2 = o2.getEndDate();
            return new NullsFirstDateComparator().compare(date1, date2);
        }
    }

    /**
     * Date comparator that puts null dates first
     */
    public static class NullsFirstDateComparator implements Comparator<Date> {
        public int compare(Date date1, Date date2) {
            // if both dates are null they are equal
            if (date1 == null && date2 == null) {
                return 0;
            }
            else if (date1 == null) {
                return -1;
            }
            else if (date2 == null) {
                return 1;
            }

            return date1.compareTo(date2);
        }
    }

    public static class ApprovalSummaryStatusComparator implements Comparator<ApprovalSummary> {

        public int compare(ApprovalSummary o1, ApprovalSummary o2) {

            WorkItem.State state1 = o1.getState();
            State state2 = o2.getState();
            if (state1 == null || state2 == null) {
                return 0;
            }
            return state1.compareTo(state2);
        }
    }
}