package sailpoint.service;

import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Notary;
import sailpoint.api.WorkflowSession;
import sailpoint.connector.ExpiredPasswordException;
import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalSet;
import sailpoint.object.Attributes;
import sailpoint.object.Comment;
import sailpoint.object.Form;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkflowCase;
import sailpoint.service.form.BaseFormStore;
import sailpoint.service.form.WorkItemFormStore;
import sailpoint.service.form.renderer.FormRenderer;
import sailpoint.service.identity.TargetAccountDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.FormHandler;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;
import sailpoint.workflow.IdentityLibrary;

/**
 * Service to perform actions on Approval Work Items. UserContext is authorized
 * in constructor to prevent unauthorized actions. Action updates are not persisted until
 * commit() method is called.
 */
public class ApprovalWorkItemService extends WorkItemService {
    
    private static final Log log = LogFactory.getLog(ApprovalWorkItemService.class);
    
    static final String PATCH_FIELD_WORK_ITEM_FORM = WorkItem.ATT_FORM;

    private static final String REQUIRE_COMMENTS_APPROVAL = "requireCommentsForApproval";
    private static final String REQUIRE_COMMENTS_DENIAL = "requireCommentsForDenial";

    private enum ApprovalItemDecision {
        Approve,
        Reject,
        Undo
    }

    private SessionStorage sessionStorage;
    private IdentityRequest identityRequest;
    private boolean workItemModified;

    /**
     * Create instance of ApprovalWorkItemService
     * @param workItemId ID of work item to access
     * @param userContext UserContext containing SailPointContext and user information
     * @throws GeneralException
     */
    public ApprovalWorkItemService(String workItemId, UserContext userContext) throws GeneralException {
        this(workItemId, userContext, null);
    }
    
    /**
     * Create instance of ApprovalWorkItemService
     * @param workItemId ID of work item to access
     * @param userContext UserContext containing SailPointContext and user information
     * @param sessionStorage used when saving a form (optional)
     * @throws GeneralException
     */
    public ApprovalWorkItemService(String workItemId, UserContext userContext, SessionStorage sessionStorage) throws GeneralException {
        super(workItemId, userContext, true);
        this.sessionStorage = sessionStorage;
        setWorkItemModified(false);
    }
    
    @Override
    protected void patchValue(String field, Object value) throws GeneralException {
        super.patchValue(field, value);
        if (PATCH_FIELD_WORK_ITEM_FORM.equals(field)) {
            saveForm(value);
        }
    }
    
    @Override
    protected List<String> getAllowedPatchFields() {
        List<String> fields = super.getAllowedPatchFields();
        fields.add(PATCH_FIELD_WORK_ITEM_FORM);
        return fields;
    }

    @Override
    protected boolean validateValue(String field, Object value) {
        boolean result = super.validateValue(field, value);
        
        return result && (PATCH_FIELD_WORK_ITEM_FORM.equals(field) ? value != null : true);
    }
    
    public boolean isWorkItemModified() {
        return this.workItemModified;
    }

    private void setWorkItemModified(boolean workItemModified) {
        this.workItemModified = workItemModified;
    }

    /**
     * Get the IdentityRequest associated with this work item
     * @return IdentityRequest
     * @throws GeneralException
     */
    public IdentityRequest getIdentityRequest() throws GeneralException {
        if (this.identityRequest == null) {
            //the identityRequestId field is a misnomer and actually refers to the name of the IdentityRequest, not the internal ID
            String identityRequestName = getWorkItem().getIdentityRequestId();
            // will sometimes have an identity request, changed this so we don't throw ObjectNotFoundException anymore
            this.identityRequest = getContext().getObjectByName(IdentityRequest.class, identityRequestName);
        }

        return this.identityRequest;
    }

    /**
     * Get the list of TargetAccounts for an approval item on this work item
     * @param approvalItemId ID of the approval item
     * @param targetIdentity Target Identity of the approval
     * @return List of TargetAccount objects
     * @throws GeneralException
     */
    public List<TargetAccountDTO> getTargetAccounts(String approvalItemId, Identity targetIdentity) throws GeneralException {
        return getApprovalItemService(approvalItemId).getTargetAccounts(getIdentityRequest(), targetIdentity);
    }

    /**
     * Get the assignment note for an approval item
     * @param approvalItemId ID of the approval item
     * @param targetIdentity the identity with the assignment note only required if operation is "Remove"
     * @return Assignment note, or null
     * @throws GeneralException
     */
    public String getAssignmentNote(String approvalItemId, Identity targetIdentity) throws GeneralException {
        return getApprovalItemService(approvalItemId).getAssignmentNote(getIdentityRequest(), targetIdentity);
    }

    /**
     * Approve a single approval item
     * @param approvalItemId Approval item ID
     * @throws GeneralException
     */
    public void approveItem(String approvalItemId) throws GeneralException {
        decideItem(approvalItemId, ApprovalItemDecision.Approve);
    }

    /**
     * Reject a single approval item
     * @param approvalItemId Approval item ID
     * @throws GeneralException
     */
    public void rejectItem(String approvalItemId) throws GeneralException {
        decideItem(approvalItemId, ApprovalItemDecision.Reject);
    }

    /**
     * Undo decision on a single approval item
     * @param approvalItemId Approval item ID
     * @throws GeneralException
     */
    public void undoItem(String approvalItemId) throws GeneralException {
        decideItem(approvalItemId, ApprovalItemDecision.Undo);
    }

    /**
     * Approve all approval items
     * @throws GeneralException
     */
    public void approveAll() throws GeneralException {
        decideAllItems(ApprovalItemDecision.Approve);
    }

    /**
     * Reject all approval items
     * @throws GeneralException
     */
    public void rejectAll() throws GeneralException {
        decideAllItems(ApprovalItemDecision.Reject);
    }

    /**
     * Complete the approval work item
     *
     * @param notary            Notary object for signing
     * @param accountId         Account ID for signing, can be null
     * @param password          Password for signing, can be null
     * @param sessionStorage    The SessionStorage to use to store WorkflowSessions if completing the
     *                          approval results in additional work items.
     * @param workItemStateType Specifies the state of the workItem after complete. If null, then the default will be 'Finished'
     *
     * @return The WorkItemResult from completing the approval.
     */
    public WorkItemResult complete(Notary notary,
                                   String accountId,
                                   String password,
                                   SessionStorage sessionStorage,
                                   String workItemStateType)
            throws GeneralException, ExpiredPasswordException {

        if (notary == null) {
            throw new GeneralException("notary is not defined");
        }

        if (null == sessionStorage) {
            throw new GeneralException("session storage is required");
        }

        // make sure work item is editable by user
        this.authorizeActions();

        // check if items are ready for completion
        validateReadyForCompletion();

        //First, sign the work item
        notary.sign(getWorkItem(), accountId, password);

        //Then, set all the completer/states
        setCompleter();
        // For nonIdentityRequestApprovals - Deny or Deny All decision will set the workItem state to Rejected
        boolean proceed = true;
        if (Util.isNotNullOrEmpty(workItemStateType) && workItemStateType.equals(WorkItem.State.Rejected.toString())) {
            proceed = false;
            getWorkItem().setState(WorkItem.State.Rejected);
        }
        else {
            getWorkItem().setState(WorkItem.State.Finished);
        }
 
        //Lastly, complete the work item and get the result.
        WorkflowSessionService svc =
            new WorkflowSessionService(getContext(), sessionStorage, getWorkItem());
        return svc.advance(getUserContext().getLoggedInUser(), proceed);
    }

    /**
     * Save and commit changes to this work item
     * @throws GeneralException
     */
    public void commit() throws GeneralException {
        getContext().saveObject(getWorkItem());
        getContext().commitTransaction();

        setWorkItemModified(false);
    }

    /**
     * Find the approval item in an approval set on the work item
     * @param approvalItemId Approval item ID
     * @return ApprovalItem
     */
    public ApprovalItem findApprovalItem(String approvalItemId) {
        return findApprovalItem(getWorkItem().getApprovalSet(), approvalItemId);
    }

    /**
     * @return true if this approval is from the Identity Update flow,
     * based on the renderer and attribute contained.
     */
    public boolean isIdentityUpdate() {
        // First check this renderer, the only thing it was used for was this specific identity update
        // scenario
        if (IdentityLibrary.IDENTITY_UPDATE_RENDERER.equals(getWorkItem().getRenderer())) {
            return true;
        }

        // Secondly if there is an approval set (only for attribute changes), look for the special IdentityUpdate flag added in 8.1
        Attributes<String, Object> attrs = getWorkItem().getAttributes();
        if (attrs != null) {
            if (attrs.get(IdentityLibrary.ARG_APPROVAL_SET) != null) {
                ApprovalSet approvalSet = (ApprovalSet) attrs.get(IdentityLibrary.ARG_APPROVAL_SET);
                for (ApprovalItem approvalItem : Util.iterate(approvalSet.getItems())) {
                    if (Util.otob(approvalItem.getAttribute(IdentityLibrary.ATTR_IDENTITY_UPDATE))) {
                        return true;
                    }
                }
            }

            // Lastly, check these special attributes on the work item
            // set for approvals in the Identity Update workflow
            if (attrs.containsKey(IdentityLibrary.ARG_LINKS_TO_MOVE) ||
                    attrs.containsKey(IdentityLibrary.ARG_LINKS_TO_REMOVE) ||
                    attrs.containsKey(IdentityLibrary.ARG_LINKS_TO_ADD) ||
                    attrs.containsKey(IdentityLibrary.ARG_OLD_ROLES_VARIABLE) ||
                    attrs.containsKey(IdentityLibrary.ARG_NEW_ROLES_VARIABLE)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Helper method to make a decision on an approval item
     * @param approvalItemId Approval item ID
     * @param decision ApprovalItemDecision to make
     * @throws GeneralException
     */
    private void decideItem(String approvalItemId, ApprovalItemDecision decision) throws GeneralException {
        // make sure work item is editable by user
        this.authorizeActions();

        ApprovalSet approvalSet = getWorkItem().getApprovalSet();
        ApprovalItem approvalItem = findApprovalItem(approvalSet, approvalItemId);
        if (approvalItem == null) {
            throw new ObjectNotFoundException(ApprovalItem.class, approvalItemId);
        }

        setDecision(approvalItem, decision);

        // validate item has required comment
        validateItemHasRequiredComment(approvalItem);

        getWorkItem().setApprovalSet(approvalSet);
        setWorkItemModified(true);
    }

    /**
     * Helper method to add required comments to all approval items on a bulk decision.
     * @param comment the comment to add
     * @param loggedInUser the identity that is making the comment
     * @throws GeneralException
     */
    public void addCommentToAllApprovalItems(String comment, Identity loggedInUser) throws GeneralException {
        // make sure work item is editable by user
        this.authorizeActions();

        if(Util.isNotNullOrEmpty(comment)) {
            ApprovalSet approvalSet = getWorkItem().getApprovalSet();
            if (!Util.isEmpty(approvalSet.getItems())) {
                for (ApprovalItem approvalItem : approvalSet.getItems()) {
                    ApprovalItemsService svc = new ApprovalItemsService(getContext(), approvalItem);
                    svc.addComment(getWorkItem(), loggedInUser, comment);
                }
            }
        }
    }

    /**
     * Helper method to make a decision on all approval items
     * @param decision ApprovalItemDecision to make
     * @throws GeneralException
     */
    private void decideAllItems(ApprovalItemDecision decision) throws GeneralException {
        // make sure work item is editable by user
        this.authorizeActions();

        ApprovalSet approvalSet = getWorkItem().getApprovalSet();
        if (!Util.isEmpty(approvalSet.getItems())) {
            for (ApprovalItem approvalItem : approvalSet.getItems()) {
                setDecision(approvalItem, decision);

                // validate item has required comment
                validateItemHasRequiredComment(approvalItem);
            }
            getWorkItem().setApprovalSet(approvalSet);
            setWorkItemModified(true);
        }
    }

    /**
     * Helper method to set decision on approval item
     * @param approvalItem ApprovalItem to decide
     * @param decision ApprovalItemDecision to make
     */
    private void setDecision(ApprovalItem approvalItem, ApprovalItemDecision decision) throws GeneralException {
        if (isIdentityUpdate()) {
            throw new GeneralException("Unable to decide approval item for identity update type");
        }
        switch (decision) {
            case Approve:
                approvalItem.approve();
                break;
            case Reject:
                approvalItem.reject();
                break;
            case Undo:
                approvalItem.undo();
                break;
        } 
    }

    /**
     * Find the approval item in an approval set
     * @param approvalSet Approval set containing the item
     * @param approvalItemId Approval item ID
     * @return ApprovalItem
     */
    private ApprovalItem findApprovalItem(ApprovalSet approvalSet, String approvalItemId) {
        ApprovalItem approvalItem = null;
        if (approvalSet != null && !Util.isEmpty(approvalSet.getItems()) && !Util.isNullOrEmpty(approvalItemId)) {
            for (ApprovalItem item : approvalSet.getItems()) {
                if (Util.nullSafeEq(approvalItemId, item.getId())) {
                    approvalItem = item;
                    break;
                }
            }

        }
        return approvalItem;
    }

    private void saveForm(Object obj) throws GeneralException {
        if (this.sessionStorage == null) {
            throw new IllegalStateException("Initialize this class with sessionStorage before saving a form.");
        }
        
        Map<String, Object> formData = (Map<String, Object>) obj;
        BaseFormStore formStore = getFormStore();
        FormHandler formHandler = new FormHandler(getContext(), formStore);
        Form expandedForm = formStore.retrieveExpandedForm();
        FormRenderer formRenderer = formStore.getFormRenderer(null);
        formRenderer.setData(formData);
    
        boolean isValid = formHandler.submit(expandedForm, formRenderer, formData);
        if (isValid) {
            // save the form to the work item, this will be committed in the superclass
            getWorkItem().setAttribute(WorkItem.ATT_FORM, expandedForm);
            // clear out the form from the store so it can be reloaded from the DB
            clearFormSessionState(formStore);
            // set the flag to true, this indicates that the original form values were updated.
            getWorkItem().setAttribute(WorkItem.ATT_FORM_CHANGED, true);
        } else {
            Map<String, String> fieldValidationErrors = formRenderer.getFieldValidation();
            /* If there were field level validation errors we need to send back the validation map.  Otherwise
             * we give the generic message */
            if (!Util.isEmpty(fieldValidationErrors)) {
                throw new ApprovalValidationException(fieldValidationErrors);
            }
            // this message is generic enough we can reuse it
            Message errorMessage = new Message(MessageKeys.LCM_MANAGE_ATTRIBUTES_FORM_VALIDATION_ERROR);
            throw new ApprovalException(errorMessage.getLocalizedMessage(getUserContext().getLocale(), getUserContext().getUserTimeZone()));
        }
    }
    
    private BaseFormStore getFormStore() {
        WorkItemFormStore store = new WorkItemFormStore(getUserContext(), getWorkItem().getId());
        store.setSessionStorage(this.sessionStorage);
        return store;
    }
    
    private void clearFormSessionState(BaseFormStore formStore) {
        formStore.clearMasterForm();
        formStore.clearExpandedForm();
        WorkflowSessionService wfsService = new WorkflowSessionService(getContext(), sessionStorage);
        wfsService.clear();
    }
    
    /**
     * Validate the work item is ready to be completed, such as all
     * the approval items having decisions or required comments.
     * @throws GeneralException
     */
    private void validateReadyForCompletion() throws GeneralException {
        boolean ready = !isWorkItemModified();
        // Do not check approval item completeness for identity update approvals,
        // there are no individual decisions allowed.
        if (!ready) {
            throw new GeneralException(MessageKeys.ERR_WORKITEM_NOT_READY_FOR_COMPLETION);
        }
        else if (!isIdentityUpdate()) {
            ApprovalSet approvalSet = getWorkItem().getApprovalSet();
            if (approvalSet != null && !Util.isEmpty(approvalSet.getItems())) {
                for (ApprovalItem item : approvalSet.getItems()) {
                    if (!item.isComplete()) {
                        throw new GeneralException(MessageKeys.ERR_WORKITEM_NOT_READY_FOR_COMPLETION);
                    }
                    // check if item has required comments
                    validateItemHasRequiredComment(item);
                }
                // all items validated
            }
        }
    }

    /**
     * Check if approval item has required comment by logged in user if required.
     * @param item ApprovalItem
     * @throws GeneralException
     */
    private void validateItemHasRequiredComment(ApprovalItem item) throws GeneralException {
        boolean itemHasRequiredComment = false;
        if ((isCommentRequiredForApproval() && item.isApproved() ||
             isCommentRequiredForDenial() && item.isRejected())) {
            List<Comment> commentsList = item.getComments();
            // Compare Identity displayable name field
            String approver = getUserContext().getLoggedInUser().getDisplayableName();
            for (Comment comment : Util.safeIterable(commentsList)) {
                if (comment.getAuthor() != null && comment.getAuthor().equals(approver)) {
                    itemHasRequiredComment = true;
                    break;
                }
            }
            // comment required but not found
            if (!itemHasRequiredComment) {
                throw new ValidationException(item.getDisplayableValue() + " missing required comments");
            }
        }
        // no comment required
    }

    /**
     * Check if a comment is required for an approve decision
     * @return true if a comment is required for approval
     */
    private boolean isCommentRequiredForApproval() {
        return isCommentRequired(true);
    }

    /**
     * Check if a comment is required for an deny decision
     * @return true if a comment is required for denial
     */
    private boolean isCommentRequiredForDenial() {
        return isCommentRequired(false);
    }

    /**
     * Check if comment required for approval or denial
     * @param isApproval true if for approval false for denials
     * @return true if comment is required
     */
    private boolean isCommentRequired(boolean isApproval) {
        WorkflowSession session = new WorkflowSession(getWorkItem());
        if (session == null) {
            return false;
        }
        WorkflowCase workflowCase = session.getWorkflowCase();
        if (workflowCase == null) {
            return false;
        }
        String requiredKey = isApproval ? REQUIRE_COMMENTS_APPROVAL : REQUIRE_COMMENTS_DENIAL;
        return Util.otob(workflowCase.get(requiredKey));
    }

    /**
     * Set the completer identity on the approval items and work item
     * 
     * @throws GeneralException
     */
    private void setCompleter() throws GeneralException {
        ApprovalSet approvalSet = getWorkItem().getApprovalSet();
        Identity completer = getUserContext().getLoggedInUser();
        String completerName = completer.getName();
        if (approvalSet != null && !Util.isEmpty(approvalSet.getItems())) {
            for (ApprovalItem item : approvalSet.getItems()) {
                item.setApprover(completerName);
            }

        }
        getWorkItem().setCompleter(completer.getDisplayableName());
    }
    
    private ApprovalItemsService getApprovalItemService(String approvalItemId) throws GeneralException {
        ApprovalItem approvalItem = findApprovalItem(approvalItemId);
        if (approvalItem == null) {
            throw new ObjectNotFoundException(ApprovalItem.class, approvalItemId);
        }
        return new ApprovalItemsService(getContext(), approvalItem);
    } 
}