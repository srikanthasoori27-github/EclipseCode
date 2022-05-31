/**
 * 
 */
package sailpoint.api.certification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CertificationService;
import sailpoint.api.Certificationer;
import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.AbstractCertificationItem;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationAction.RemediationAction;
import sailpoint.object.CertificationAction.Status;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.CertificationItem.Type;
import sailpoint.object.Duration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.LimitReassignmentException;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.RemediationItem;
import sailpoint.object.SelfCertificationException;
import sailpoint.object.WorkItem;
import sailpoint.service.SelectionCriteria;
import sailpoint.service.SelectionModel;
import sailpoint.service.certification.BaseDecision;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.tools.EmailException;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Pair;
import sailpoint.tools.Util;
import sailpoint.web.certification.PolicyTreeNode;
import sailpoint.web.certification.PolicyViolationJsonUtil;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.view.LineItem;


/**
 * @author peter.holcomb
 *
 */
public class CertificationDecisioner {

    private static final Log log = LogFactory.getLog(CertificationDecisioner.class);

    public static final String STATUS_UNDO_DELEGATION = "UndoDelegation";

    public static final String STATUS_UNDO = "Undo";

    public static final String STATUS_REASSIGN = "Reassign";

    public static final String STATUS_ACCOUNT_REASSIGN = "AccountReassign";

    public static final String STATUS_APPROVE_ACCOUNT = "ApproveAccount";

    public static final String STATUS_DELEGATION_REVIEW_REJECT = "RejectDelegationReview";

    public static final String STATUS_DELEGATION_REVIEW_ACCEPT = "AcceptDelegationReview";

    @Deprecated
    public static final String STATUS_ENTITY_CLASSIFICATION = "saveEntityCustomFields";

    public static final String SCOPE_ITEM = "CertificationItem";
    public static final String SCOPE_ENTITY = "CertificationEntity";

    public static final String DELEGATION_REVIEW_ACTION_ACCEPT = "Accept";
    public static final String DELEGATION_REVIEW_ACTION_REJECT = "Reject";

    public static final String CHALLENGE_ACCEPT = "Accept";
    public static final String CHALLENGE_REJECT = "Reject";

    /**
     * Maximum size of an IN query before we break it up.
     * Limit is 2100 on SQL Server, but we will just use 1000 for safety
     */
    private static final int MAX_IN_QUERY_SIZE = 1000;

    /**
     * Context given to us by the creator.  We can commit transactions.
     */
    SailPointContext context;

    /**
     * The id of the certification we are working on
     */
    String certificationId;

    /**
     * The certification object we are working on.
     * Used instead of the id if provided in the constructor.
     */
    Certification certification;

    /**
     * The certification item we are currently working on
     */
    CertificationItem item;

    ProvisioningPlan additionalActions;
    
    /** The list of items in a bulk action that we could not certify for one
     * reason or another */
    List<String> invalidItems;

    /** 
     * Any errors that we encounter along the way as we do our work.
     * @param context
     */
    List<String> errors;

    /** 
     * Any errors that we encounter along the way as we do our work.
     * @param context
     */
    List<String> warnings;

    /** 
     * The identity who is performing the action
     */
    Identity decider;


    /** 
     * A map to contain any item ids that were rejected and an error message about why
     * they were rejected
     */
    Map<String,String> rejections;

    /**
     * List of violations items we've processed.
     */
    Set<String> violationRemediations;
    
    /**
     * Flag to determine if we want the warnings list for invalidItems to be built here or not.
     * For responsive cert types, this flag will be set to false.
     */
    boolean buildWarningsForInvalidItems;

    public boolean getBuildWarningsForInvalidItems() {
        return buildWarningsForInvalidItems;
    }

    public void setBuildWarningsForInvalidItems(boolean buildWarningsForInvalidItems) {
        this.buildWarningsForInvalidItems = buildWarningsForInvalidItems;
    }

    public CertificationDecisioner() {
        this.rejections = new HashMap<>();
        this.warnings = new ArrayList<>();
        this.errors = new ArrayList<>();
        this.invalidItems = new ArrayList<>();
        violationRemediations = new HashSet<>();
    }


    public CertificationDecisioner(SailPointContext context, String certificationId, Identity decider) {
        this();
        this.context = context;
        this.certificationId = certificationId;
        this.decider = decider;
    }

    public CertificationDecisioner(SailPointContext context, Certification certification, Identity decider) {
        this(context, certification.getId(), decider);
        this.certification = certification;
    }

    /**
     * Perform actions on a list of decision objects 
     * @param decisions List of Decision objects
     * @return Fully initialized DecisionResults
     */
    public DecisionResults decide(final List<Decision> decisions) throws GeneralException {
        return decide(decisions, false);
    }
    
    /**
     * Perform actions on a list of decision objects, optionally with only simple results (no cert stats) 
     * @param decisions List of Decision objects
     * @param simpleResult If true, only simple results needed. Skip stats and additional info.                 
     * @return DecisionResults
     */
    public DecisionResults decide(final List<Decision> decisions, final boolean simpleResult) throws GeneralException{

        Callable<DecisionResults> doWhat = new Callable<CertificationDecisioner.DecisionResults>() {
            
            @Override
            public DecisionResults call() throws Exception {
        
                return decideInternal(decisions, simpleResult);
            }
        };
        
        Certification certification = getCertification();
        if(certification != null && certification.getSigned() != null) {
            //Ensure we're not saving over someone else's signoff.
            errors.add("Certification was already signed!");
            DecisionResults decisionResults = new DecisionResults();
            decisionResults.setErrors(errors);
            return decisionResults;
        }
        
        // doWithCertLock is null friendly and will return an appropriate Pair. Skipping null check.
        Pair<Boolean, DecisionResults> returnValue = ObjectUtil.doWithCertLock(context, certification, doWhat, true, 2);
        if (returnValue.getFirst() == false) {
            DecisionResults decisionResults = new DecisionResults();
            decisionResults.setTimedOut(true);
            return decisionResults;
        }
        
        return returnValue.getSecond();
    }

    private DecisionResults decideInternal(List<Decision> decisions, boolean simpleResult)
            throws GeneralException {
        // When the cert is locked, a full decache() is performed.  Gotta make
        // sure this decider is attached back fully or we get lazy initialization erros. 
        this.decider = ObjectUtil.reattach(getContext(), this.decider);

        // Handle 'priority' decisions - decisions such
        // as delegation revokes or delegation reviews which
        // need to be executed before other decisions
        int count = 0;
        for(Decision decision : decisions) {
            if (decision.isPriorityDecision()){
                handleDecision(decision);
                count++;
                if (count % 20 == 0) {
                    this.commitAndDecache();
                }
            }
        }

        // Handle all generic decisions
        for(Decision decision : decisions) {
            if (!decision.isPriorityDecision()){
                handleDecision(decision);
                count++;
                if (count % 20 == 0) {
                    this.commitAndDecache();
                }
            }
        }

        if (count % 20 != 0) {
            this.commitAndDecache();
        }

        // Build out associations between violation remediations
        // We wait until everything is processed so that we can determine
        // which child items were not independently certified.
        if (violationRemediations != null){
            for(String violationItemId : violationRemediations){
                associationRemediations(violationItemId);
            }
        }


        try {
            saveAndRefreshCertification();
        } catch(GeneralException ge) {
            if (log.isErrorEnabled())   
                log.error("Unable to save and refresh certification: " + ge.getMessage(), ge);
            
            errors.add(ge.getMessage());
        }
        
        // null out any cleared actions so the UI continues to play nice -
        // must be done AFTER saveAndRefreshCertification() so that history 
        // items can get created for any decisions cleared from continuous certs  
        for(Decision decision : decisions) {
            if (isDecisionStatusUndo(decision.getStatus())) {
                nullClearedActions(decision);
            }
        }
        return buildSummary(decisions, simpleResult);
    }
    
    /**
     * Verify work item is complete
     * 
     * 1) If this is a certification delegation work item, check if the item(s)
     * that were delegated are all finished (if delegation completion is
     * required).  
     * 2) If this is a remediation work item, check that all remediation items
     * are marked as complete.
     * 
     * If not complete, this returns false and adds a warning to the page.
     */
    private boolean isComplete(WorkItem item) 
        throws GeneralException 
    {
        boolean isComplete = true;
        
        // If this is a delegation and delegation completion is required, check
        // if the delegation is done.
        if (WorkItem.Type.Delegation.equals(item.getType())) {

            // Get either the CertificationItem or CertificationEntity.
            AbstractCertificationItem certItem = null;
            if (null != item.getCertificationItem()) {
                certItem = item.getCertificationItem(getContext());
            } else {
                certItem = item.getCertificationEntity(getContext());
            }

            // If not complete, add a message and set isComplete to false.
            if ((null != certItem) && !isFullyCompleted(certItem)) {
                isComplete = false;
            }
        }
        
        // If this is a remediation, check that all remediation items are complete
        if (WorkItem.Type.Remediation.equals(item.getType())) {
            List<RemediationItem> remItems = item.getRemediationItems();
            for (RemediationItem remItem : remItems) {
                if (!remItem.isComplete()) {
                    isComplete = false;
                    break;
                }
            }
        }

        return isComplete;
    }
    
    /**
     * Check if the given item is complete.  This looks for non-null decisions
     * rather than the isComplete().  Since delegated items are not considered
     * complete, isComplete() will always return false here..
     */
    private boolean isFullyCompleted(AbstractCertificationItem item)
        throws GeneralException {
        
        // This is a leaf - CertificationItem - check for a decision.
        if (isLeaf(item)) {
            if (!actionHasDecision(item.getAction())) {
                return false;
            }
        }
        else {
            // Not a leaf - recurse.
            for (CertificationItem subItem : item.getItems()) {
                if (!isFullyCompleted(subItem)) {
                    return false;
                }
            }
        }

        // If the item/entity has all decisions made, run the completion rule
        // (if there is one) for the final say as to whether this is done.
        Certificationer certificationer = new Certificationer(getContext());
        return certificationer.isCompletePerCompletionRule(item);
    }
    
    /**
     * Gets whether or not the certification item is a leaf.
     * @param item The item to check.
     * @return True if the certification item is a leaf (no children), false otherwise.
     */
    private boolean isLeaf(AbstractCertificationItem item) 
    {
        return (item.getItems() == null) || item.getItems().isEmpty();
    }
    
    /**
     * Gets whether or not a CertificationAction has a decision.
     * @param action The action to check.
     * @return True if the action has a decision that is not Cleared.
     *         If action is null, false is returned.
     */
    private boolean actionHasDecision(CertificationAction action)
    {
        if (action == null) {
            return false;
        }
        
        return action.getStatus() != null && !action.getStatus().equals(CertificationAction.Status.Cleared);
    }

    private void handleDecision(Decision decision) throws GeneralException{

        if (decision.isEntityDecision()){
            handleEntityDecision(decision);
        } else {
            handleItemDecision(decision);
        }
    }

    private void handleItemDecision(Decision decision) throws GeneralException{

        List<String> itemIds = getObjectIds(decision, CertificationItem.class, false);

        if (STATUS_REASSIGN.equals(decision.getStatus()) || STATUS_ACCOUNT_REASSIGN.equals(decision.getStatus())){
            if (STATUS_ACCOUNT_REASSIGN.equals(decision.getStatus())){
                decision.setStatus(STATUS_REASSIGN); // STATUS_ACCOUNT_REASSIG isnt a valid status
                if (decision.getDescription() == null)
                    decision.setDescription(Message.localize(MessageKeys.ACCOUNT_REASSIGN_DESC).getLocalizedMessage());
            }
            reassign(decision, itemIds, CertificationItem.class);
        } else {
            /** Reset additional actions **/
            additionalActions = null;

            int cnt = 0;
            for(String itemId : itemIds) {
                try {
                    item = getContext().getObjectById(CertificationItem.class, itemId);

                    // This is the classic UI version of revoke delegation
                    if (STATUS_UNDO_DELEGATION.equals(decision.getStatus())) {
                        if (item.getDelegation() != null && !item.getDelegation().isRevoked()) {
                            this.revokeDelegation();
                        }
                    }
                    // And this is the new UI version, which does the revoke delegation and new status in one step
                    else if (decision.isRevokeDelegation()) {
                        if (item.getDelegation() != null && !item.getDelegation().isRevoked()) {
                            this.revokeDelegation();
                        }
                        if (decision.getStatus() != null) {
                            doSetStatus(decision);
                        }
                    } else if (STATUS_DELEGATION_REVIEW_ACCEPT.equals(decision.getStatus()) ||
                            DELEGATION_REVIEW_ACTION_ACCEPT.equals(decision.getDelegationReviewAction())) {
                        // Both classic and responsive UI delegation review accept. Does not also contain new status.
                        if (item.getAction() != null) {
                            item.review(getDecider(), item.getAction());
                        }
                    } else if (STATUS_DELEGATION_REVIEW_REJECT.equals(decision.getStatus())) {
                        // Classic UI delegation review reject. Does not also contain new status.
                        if (item.getAction() != null) {
                            // The item has been reviewed and rejected.
                            // Undo the decision that the delegate made.
                            undo(decision);
                        }
                    } else if (DELEGATION_REVIEW_ACTION_REJECT.equals(decision.getDelegationReviewAction())) {
                        // Responsive UI delegation review reject. Also contains new status which must be applied.
                        if (item.getAction() != null) {
                            // The item has been reviewed and rejected.
                            // Undo the decision that the delegate made.
                            undo(decision);
                        }
                        doSetStatus(decision);
                    }
                    else if(Status.Delegated.name().equals(decision.getStatus())) {
                        // Don't allow item to be delegated to the target, unless that identity can self certify.
                        Identity recipient = getIdentityOrWorkgroup(decision.getRecipient());
                        SelfCertificationChecker selfCertificationChecker = new SelfCertificationChecker(getContext(), this.certificationId);

                        // Ensure the user cannot delegate unto thynself.
                        if (recipient != null && selfCertificationChecker.isSelfCertify(recipient, Util.asList(item))) {
                            Message message = new Message(MessageKeys.ERR_CANNOT_SELF_CERTIFY, recipient.getDisplayableName());
                            errors.add(message.getLocalizedMessage());
                            throw new GeneralException("Cannot delegate certitem (" + item.getId() + ") to certitem.identity or a workgroup that certitem.identity is a member of.");
                        }

                        doSetStatus(decision);
                    }
                    else if (!delegationBlocksDecision(item, decision)){
                        /** Check for bulk decision to see if we can bulk act on this item, then handle it using the bulkCertify
                         * method on certItem **/
                        if(decision.getSelectionCriteria().isBulk() && !CertificationAction.Status.RevokeAccount.name().equals(decision.getStatus())) {
                            bulkDecide(decision);
                        }
                        // Check for oneStepChallenge/Accept first
                        else if (item.getChallenge() != null && decision.getChallengeAction() != null &&
                                decision.isOneStepChallenge() && CHALLENGE_ACCEPT.equals(decision.getChallengeAction())) {
                            saveChallengeDecision(decision);
                            // agree with the challenger so go ahead and set the status (responsive UI)
                            doSetStatus(decision);
                        }
                        // Otherwise this is from the classic UI, or we reject the challenge (responsive UI)
                        else if (item.getChallenge() != null && decision.getChallengeAction() != null){
                            saveChallengeDecision(decision);
                        }
                        else if (item!=null) {
                            doSetStatus(decision);
                        }
                    }

                    cnt++;

                    if (cnt % 20 == 0){
                        this.commitAndDecache();
                    }

                }
                catch (GeneralException ge) {
                    if (log.isWarnEnabled()) {
                        log.warn("Unable to make decision on item id [" + itemId + "]: " + ge.getMessage(), ge);
                    }

                    // What's the point of this?  It is not getting used anywhere.
                    rejections.put(itemId, ge.getMessage());
                }
            }
        }
        markItemsForRefresh(itemIds);
    }

    /**
     * Handles a simple status change on the decision
     * @param decision the decision
     * @throws GeneralException
     */
    private void doSetStatus(Decision decision) throws GeneralException {
        if (isDecisionStatusUndo(decision.getStatus())) {
            undo(decision);
        } else if(STATUS_APPROVE_ACCOUNT.equals(decision.getStatus())) {
            approveAccount(decision);
        } else if(CertificationAction.Status.Approved.name().equals(decision.getStatus())) {
            approve(decision);
        } else if(CertificationAction.Status.Remediated.name().equals(decision.getStatus())) {
            if (remediate(decision) && CertificationItem.Type.PolicyViolation.equals(item.getType()))
                violationRemediations.add(item.getId());
        } else if (CertificationAction.Status.RevokeAccount.name().equals(decision.getStatus())) {
            revoke(decision);
        } else if (CertificationAction.Status.Mitigated.name().equals(decision.getStatus())) {
            mitigate(decision);
        } else if (CertificationAction.Status.Delegated.name().equals(decision.getStatus())) {
            delegate(decision);
        } else {
            if (log.isWarnEnabled()) {
                log.warn("Unable to find action for status: " + decision.getStatus());
            }
        }
    }

     /*
     * Marks the items as needing refresh
     */
    private void markItemsForRefresh(List<String> itemIds) throws GeneralException {
        SailPointContext ctx = getContext();
        int count = 0;
        IncrementalObjectIterator<CertificationItem> items = new IncrementalObjectIterator(ctx, CertificationItem.class, itemIds);
        while (items.hasNext()) {
            count++;
            CertificationItem item = items.next();
            item.setNeedsRefresh(true);
            ctx.saveObject(item);
            if (count % 20 == 0) {
                this.commitAndDecache();
            }
        }
        ctx.commitTransaction();
    }

    /**
     * Create associations between actions. The simple case is when a role
     * is revoked through the revocation of a role sod violation.
     */
    private void associationRemediations(String itemId) throws GeneralException{

        CertificationItem violationItem = context.getObjectById(CertificationItem.class, itemId);
        List<String> roles = violationItem.getPolicyViolation() != null ?
                violationItem.getPolicyViolation().getBundleNamesMarkedForRemediation() : new ArrayList<>();
        
        for (String role : roles) {
            QueryOptions ops = new QueryOptions(Filter.eq("parent", violationItem.getParent()));
            ops.add(Filter.eq("bundle", role));
            List<CertificationItem> roleItems = context.getObjects(CertificationItem.class, ops);
            handleChildActions(violationItem, roleItems);
        }

        List<PolicyTreeNode> entitlements = violationItem.getPolicyViolation().getEntitlementsToRemediate();
        if (entitlements != null) {
            Certification.EntitlementGranularity granularity = violationItem.getCertification().getEntitlementGranularity();
            for (PolicyTreeNode policyNode : entitlements) {
                QueryOptions ops = new QueryOptions(Filter.eq("parent", violationItem.getParent()));
                ops.add(Filter.eq("exceptionApplication", policyNode.getApplication()));
                if (Certification.EntitlementGranularity.Attribute.equals(granularity)
                        || Certification.EntitlementGranularity.Value.equals(granularity)) {
                    if (policyNode.isPermission()) {
                        ops.add(Filter.eq("exceptionPermissionTarget", policyNode.getName()));
                    } else {
                        ops.add(Filter.eq("exceptionAttributeName", policyNode.getName()));
                    }

                    if (Certification.EntitlementGranularity.Value.equals(granularity)) {
                        if (policyNode.isPermission()) {
                            ops.add(Filter.eq("exceptionPermissionRight", policyNode.getValue()));
                        } else {
                            ops.add(Filter.eq("exceptionAttributeValue", policyNode.getValue()));
                        }
                    }
                }

                List<CertificationItem> entitlementItems = context.getObjects(CertificationItem.class, ops);
                handleChildActions(violationItem, entitlementItems);
            }
        }
        
        this.commitAndDecache();
    }

    /**
     * Helper method to handle logic around associated child actions with the violation item
     */
    private void handleChildActions(CertificationItem violationItem, List<CertificationItem> dependentItems) throws GeneralException {
        for (CertificationItem dependentItem : Util.safeIterable(dependentItems)) {
            if (dependentItem.getAction() == null || 
                    CertificationAction.Status.Cleared.equals(dependentItem.getAction().getStatus())) {
                // bug #21467 - If violationItem has a delegation, this is a line item delegation, we want to set that
                // on the entitlement item as well. Otherwise we will fail case 4 in certificationItem.checkForDecisionErrors
                if (dependentItem.getDelegation() == null && violationItem.getDelegation() != null) {
                    dependentItem.setDelegation(violationItem.getDelegation());
                }

                if (dependentItem.checkForDecisionErrors(getDecider(), violationItem.getAction().getActingWorkItem(),
                        Status.Remediated, true) == null) {
                    dependentItem.remediate(getContext(), getDecider(), violationItem.getAction().getActingWorkItem(), null,
                            violationItem.getAction().getOwnerName(), null, violationItem.getAction().getComments(),
                            null, null);
                    dependentItem.getAction().setSourceAction(violationItem.getAction());
                    violationItem.getAction().addChildAction(dependentItem.getAction());
                    context.saveObject(dependentItem);
                }
            } else if (CertificationAction.Status.Remediated.equals(dependentItem.getAction().getStatus()) ||
                    Status.RevokeAccount.equals(dependentItem.getAction().getStatus())){
                // add required
                violationItem.getAction().addChildAction(dependentItem.getAction());
            }
        }
    }

    /**
     * A decision cannot be made on a delegation item unless the decision was made
     * from within the delegation workitem, the decision includes a delegation revoke
     * or if the delegation is inactive or already revoked.
     */
    private boolean delegationBlocksDecision(CertificationItem item, Decision decision){

        if (item.getDelegation() != null){
            // Check if the decision is made within the workitem for the delegation
            if (item.getDelegation().getWorkItem() != null && 
                    item.getDelegation().getWorkItem().equals(decision.getWorkItemId()))
                return false;

            // if the delegation is active and not revoked
            if (!decision.revokeDelegation && item.getDelegation().isActive() && !item.getDelegation().isRevoked() )
                return true;
        }

        if (item.getParent().getDelegation() != null){
            String delegationWorkItem = item.getParent().getDelegation().getWorkItem();
            if (delegationWorkItem != null && delegationWorkItem.equals(decision.getWorkItemId()))
                return false;
            if (!decision.revokeEntityDelegation && item.getParent().getDelegation().isActive() &&
                    !item.getParent().getDelegation().isRevoked() )
                return true;
        }

        return false;
    }

    private void handleEntityDecision(Decision decision) throws GeneralException{

        List<String> entityIds = decision.isDecisionScope(SCOPE_ITEM) ? getDistinctEntities(decision) :
                getObjectIds(decision, CertificationEntity.class, false);

        if (Util.isEmpty(entityIds)) {
            return;
        }

        if (STATUS_REASSIGN.equals(decision.getStatus())){
            reassign(decision, entityIds, CertificationEntity.class);
        } else if (CertificationAction.Status.Delegated.name().equals(decision.getStatus())){
            delegateEntities(decision, entityIds);
        } else if(STATUS_UNDO_DELEGATION.equals(decision.getStatus())) {
            // dont allow revoke of an entity delegation from within a workitem
            if (decision.getWorkItemId() == null)
            revokeEntityDelegations(entityIds);
        } else if (!Util.isEmpty(entityIds)){

            /** Reset additional actions **/
            additionalActions = null;

            int cnt = 0;
            List<String> itemIds = getItemIds(decision, entityIds);
            for (String itemId : itemIds) {
                item = getContext().getObjectById(CertificationItem.class, itemId);
                try {
                    boolean revokeDelegation =
                        isDecisionStatusUndo(decision.getStatus()) &&
                        decision.isRevokeDelegation() &&
                        item.getDelegation() != null && item.getDelegation().isActive();

                    String error = null;

                    // We should be checking for decision errors always.  Historically
                    // we have done this for revokeDelegation.  I'm adding this for
                    // acting within a delegated work item to address bug 14776.  It's
                    // a little close to the end of the release to do this for every
                    // entity decision since this has been a source of problems in the
                    // past.
                    if (revokeDelegation || (null != decision.getWorkItemId())) {
                        error = item.checkForDecisionErrors(getDecider(), decision.getWorkItemId(), null, true);
                    }

                    if (null == error) {
                        if (revokeDelegation){
                            item.revokeDelegation();
                        }
                        else {
                            bulkDecide(decision);
                        }
                    }
                    else {
                        addInvalidItem(Arrays.asList(item), null);
                    }
                } catch(GeneralException ge) {
                    if (log.isWarnEnabled())
                        log.warn("Unable to make decision on item id [" + itemId + "]: " +
                                 ge.getMessage(), ge);
                    
                    rejections.put(itemId, ge.getMessage());
                }

                if (cnt % 200 == 0){
                    this.commitAndDecache();
                }

                cnt++;
            }
        }
        List<String> itemIds = new ArrayList<>();
        for (String entityId : entityIds) {
            QueryOptions opts = new QueryOptions();
            opts.add(Filter.eq("parent.id", entityId));
            Iterator<Object[]> results = context.search(CertificationItem.class, opts, "id");
            while (results != null && results.hasNext()) {
                itemIds.add((String)results.next()[0]);
            }
            
        }
        markItemsForRefresh(itemIds);
    }

    private void revokeEntityDelegations(List<String> entityIds) throws GeneralException{

        if (entityIds == null || entityIds.isEmpty())
            return;

        int cnt = 0;
        for (String entityId : entityIds){
           CertificationEntity entity = getContext().getObjectById(CertificationEntity.class, entityId);
           if (entity.getDelegation() != null)
               entity.revokeDelegation();
           if (cnt % 20 == 0){
               this.commitAndDecache();
           }
           cnt++;
       }

       if (cnt > 0){
          this.commitAndDecache();
       }
    }

    private void bulkDecide(Decision decision) throws GeneralException {
        if (isDecisionStatusUndo(decision.getStatus())) {
            String error = item.checkForDecisionErrors(getDecider(), decision.getWorkItemId() ,null, true);
            if (error == null){
                item.clearDecision(getContext(), getDecider(), decision.getWorkItemId());
            } else {
                addInvalidItem(Arrays.asList(item), null);
            }
        } else {

            CertificationAction.Status status =
                CertificationAction.Status.valueOf(decision.getStatus());

            // dont allow revocation of an acount - use RevokeAccount instead
            if (CertificationAction.Status.Remediated.equals(status) &&
                    item.getType().equals(CertificationItem.Type.Account)){
                return;
            }

            // RevokeAccount makes no sense in the context of a PV. Bail here
            // b/c otherwise we will send invalid items messages back to the user
            // regarding the policy violation
            if (CertificationAction.Status.RevokeAccount.equals(status) &&
                    item.getType().equals(CertificationItem.Type.PolicyViolation)){
                return;
            }

            // Revoke account is really a remediation with revokeAccount set to true.
            boolean isRevokeAccount = false;
            if (CertificationAction.Status.RevokeAccount.name().equals(decision.getStatus())) {
                status = CertificationAction.Status.Remediated;
                isRevokeAccount = true;
            }

            CertificationAction action = new CertificationAction();
            action.setStatus(status);
            action.setRevokeAccount(isRevokeAccount);
            action.setComments(decision.getComments());
            action.setMitigationExpiration(calculateMitigationExpiration(decision));
            action.setDescription(this.getDescription(decision));

            if (isDecisionStatusUndo(decision.getStatus()) && decision.isRevokeDelegation()){
                item.revokeDelegation();
            }

            addInvalidItem(item.bulkCertify(getDecider(), decision.getWorkItemId(), action, null, false), status);
        }
    }

    /**
     * Returns true if decision has a status of Cleared or Undo
     * @param {String} decisionStatus  The status to check.
     * @return {boolean} True if status is Cleared or Undo, False otherwise.
     */
    private boolean isDecisionStatusUndo(String decisionStatus)
    {
        if (decisionStatus == null) {
            return false;
        }
        return STATUS_UNDO.equals(decisionStatus) || CertificationAction.Status.Cleared.name().equals(decisionStatus);
    }

    void saveChallengeDecision(Decision decision){
        CertificationService svc = new CertificationService(getContext());
        try {
            if (CHALLENGE_REJECT.equals(decision.getChallengeAction())){
               svc.rejectChallenge(getDecider(), decision.getChallengeComments(), item);
            }
            else {
               svc.acceptChallenge(getDecider(), decision.getChallengeComments(), item);
            }
        } catch (GeneralException e) {
            // This gets thrown if the decision is made after the challenge
            // period is over.  Consider making this a more granular
            // exception.
            Message.Type newType = e instanceof EmailException ? Message.Type.Warn
                   :  Message.Type.Error;
            Message msg = e.getMessageInstance();
            msg.setType(newType);
            errors.add(msg.getLocalizedMessage());
        }

    }

    void approveAccount(Decision decision) throws GeneralException{
        item.approveAccount(getContext(), getDecider(), decision.getWorkItemId(), decision.getComments());
    }

    /**
     * Performs an approval on a list of certification items
     * @param decision
     */
    void approve(Decision decision) throws GeneralException {
        if (decision.provisionMissingRoles){

            ProvisioningPlan additionalActions = new ProvisioningPlan();

            ProvisioningPlan.AccountRequest accountRequest = new ProvisioningPlan.AccountRequest(ProvisioningPlan.AccountRequest.Operation.Modify,
                    ProvisioningPlan.APP_IIQ, null, item.getIdentity());
            additionalActions.add(accountRequest);

            ProvisioningPlan.AttributeRequest attrReq = new ProvisioningPlan.AttributeRequest();
            attrReq.setOperation(ProvisioningPlan.Operation.Add);
            attrReq.setName(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES);
            attrReq.setValue(item.getBundle());
            accountRequest.add(attrReq);

            Identity recipient = getIdentityOrWorkgroup(decision.getRecipient());
            item.approve(getContext(), getDecider(), decision.getWorkItemId(), additionalActions,
                        recipient != null ? recipient.getName() : null, null,
                        decision.getComments());
        } else {
            item.approve(getContext(), getDecider(), decision.getWorkItemId(), decision.getComments(),
                    decision.isAutoDecision());
        }
    }

    boolean remediate(Decision decision) throws GeneralException {

        if(item.getType().equals(Type.PolicyViolation)) {

            // Make sure this policy allows remediations
            Policy p = item.getPolicyViolation().getPolicy(getContext());
            if (p != null && !p.isActionAllowed(CertificationAction.Status.Remediated)){
                Identity recipient = getRemediationRecipient(decision, null);
                String recipientName = recipient != null ? recipient.getDisplayableName() : decision.getRecipient();
                Message message = new Message(MessageKeys.ERR_CANT_REMEDIATE_VIOLATION,
                        item.getPolicyViolation().getDisplayableName(), recipientName);
                errors.add(message.getLocalizedMessage());
                return false;
            }

            remediateViolation(decision);
        }

        RemediationManager.ProvisioningPlanSummary plan = null;
        if (item!=null) {
            RemediationManager remediationMgr = new RemediationManager(getContext());
            plan = remediationMgr.calculateRemediationDetails(item, Status.valueOf(decision.getStatus()));
        }

        
        boolean requireRemediationWorkItem = (RemediationAction.OpenWorkItem.equals(plan.getAction()) ||
                RemediationAction.NoActionRequired.equals(plan.getAction()));
        
        // Only set a recipient if we're opening a work item.
        String recipName = null;
        if (requireRemediationWorkItem) {
            Identity recipient = getRemediationRecipient(decision, plan.getUnmanagedPlan());
            recipName = recipient != null ? recipient.getName() : null;
        }

        item.remediate(getContext(), getDecider(), decision.getWorkItemId(), plan.getAction(),
                recipName, this.getDescription(decision), decision.getComments(),
                getRemediationDetails(decision, plan), null);

        return true;
    }
    
    /**
     * Return the recipient of the given remediation - either the recipient from
     * the decision or the default remediator for the item.
     */
    private Identity getRemediationRecipient(Decision decision, ProvisioningPlan unmanagedPlan)
        throws GeneralException {

        Identity recipient = getIdentityOrWorkgroup(decision.getRecipient());
        if (recipient == null) {
            Identity defaultRemediator = null;
            if (unmanagedPlan != null) {
                defaultRemediator = RemediationManager.getDefaultRemediator(unmanagedPlan, context);
            }
            if (defaultRemediator == null) {
                defaultRemediator = item.getDefaultRemediator(context);
            }
            
            if (defaultRemediator != null) {
                recipient = defaultRemediator;
            }
        }
        
        return recipient;
    }

    /** Gets the violation for the current item and updates it with the list of
     * bundles or entitlements we are remediating
     */
    void remediateViolation(Decision decision) throws GeneralException{
        PolicyViolation violation = item.getPolicyViolation();
        boolean updated = false;
        if(violation!=null) {
            /** Set the list of bundles to be remediated on the policy violation **/
            if(decision.getRevokedRoles()!=null && !decision.getRevokedRoles().isEmpty()) {
                violation.setBundleNamesMarkedForRemediation(decision.getRevokedRoles());
                updated = true;
            } else if (decision.getSelectedViolationEntitlements() != null){
                List<PolicyTreeNode> response = PolicyViolationJsonUtil.decodeSelectedEntitlementsJson(decision.getSelectedViolationEntitlements());
                violation.setEntitlementsToRemediate(response);
                updated = true;
            }
        }
        
        try {
            if (updated){
                getContext().saveObject(item);
                getContext().commitTransaction();
            }
        } catch(GeneralException ge) {
            if (log.isWarnEnabled())
                log.warn("Unable to save certification item: " + ge.getMessage(), ge);
        }
    }

    void mitigate(Decision decision) throws GeneralException {
        if (decision.isMitigationExpiresNextCert())
            item.acknowledge(getContext(), getDecider(), decision.getWorkItemId(), decision.getComments());
        else {
            Date expDate = calculateMitigationExpiration(decision);
            item.mitigate(getContext(), getDecider(), decision.getWorkItemId(), expDate, decision.getComments());
        }
    }

    private Date calculateMitigationExpiration(Decision decision) throws GeneralException {
        Date expDate = null;

        if (CertificationAction.Status.Mitigated.name().equals(decision.getStatus()) &&
            !decision.isMitigationExpiresNextCert()) {

            expDate = decision.getMitigationExpirationDate() != null ?
                    new Date(decision.getMitigationExpirationDate()) : null;

            if (null == expDate) {
                CertificationDefinition certDef = getCertification().getCertificationDefinition(getContext());
                if (certDef!=null) {
                    /** Need to fix the expiration date if they've set it farther away than the allowed duration date **/
                    Long amount = certDef.getAllowExceptionDurationAmount(getContext());
                    Duration.Scale scale = certDef.getAllowExceptionDurationScale(getContext());
                    if (amount != null && scale != null) {
                        Duration allowExceptionDuration = new Duration(amount, scale);
                        expDate = allowExceptionDuration.addTo(new Date());
                    }
                }
            }
        }

        return expDate;
    }
    
    void delegate(Decision decision) throws GeneralException {
        Identity recipient = getDelegationRecipient(decision, item);
        if (recipient == null) {
            //There was some error getting recipient, just return
            return;
        }
        
        item.delegate(getDecider(), decision.getWorkItemId(),
                recipient.getName(), this.getDescription(decision), decision.getComments());
    }

    /**
     * Delegates a list of entities one at a time.
     * @param decision the decision
     * @param entityIds the list of entity ids
     * @throws GeneralException
     */
    void delegateEntities(Decision decision, List<String> entityIds) throws GeneralException {
        Identity recipient = getIdentityOrWorkgroup(decision.getRecipient());
        if (recipient == null) {
            //There was some error getting recipient, just return
            return;
        }

        // Check for self-certification first to avoid pulling in items if we are just going
        // to fail anyway.
        SelfCertificationChecker selfCertChecker = new SelfCertificationChecker(getContext(), certificationId);
        if (selfCertChecker.isSelfCertify(recipient, entityIds, CertificationEntity.class)) {
            Message message = new Message(Message.Type.Error,
                    MessageKeys.ERR_CANNOT_SELF_CERTIFY_DELEGATE,
                    recipient.getDisplayableName());
            errors.add(message.getLocalizedMessage());
            return;
        }

        // Get all the entities and iterate over them. We assume this list won't be ginormous.
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.in("id", entityIds));
        List<CertificationEntity> entities = this.getContext().getObjects(CertificationEntity.class, qo);

        for (CertificationEntity entity : entities) {
            // Remove all auto-approval decisions on the entity so that the delegate
            // gets a clean slate to make decisions.
            entity.removeAutoApprovals(getContext(), decider, decision.getWorkItemId());

            entity.delegate(decider, decision.getWorkItemId(), recipient.getName(),
                    this.getDescription(decision, entity), decision.getComments());
        }
    }

    /**
     * Retrieve the Identity (or workgroup) from the decision. Validate the recipient is
     * specified and check for self certification.
     * Errors messages will be added here and return value will be null if validation fails 

     * @param decision 
     * @param item The target item/entity we are delegating. Used to check for self-certification
     * @return Identity to receive delegation, or null if validation fails
     *
     * @throws GeneralException
     */
    private Identity getDelegationRecipient(Decision decision, AbstractCertificationItem item) 
    throws GeneralException {
        Identity recipient = getIdentityOrWorkgroup(decision.getRecipient());
        if(recipient==null) {
            Message message = new Message(MessageKeys.ERR_DELEGATION_NO_RECIPIENT, decision.getRecipient());
            errors.add(message.getLocalizedMessage());
            return null;
        }

        // Make sure that the user isn't being delegated to themself.
        SelfCertificationChecker selfCertificationChecker = new SelfCertificationChecker(getContext(), certificationId);
        if (selfCertificationChecker.isSelfCertify(recipient, Util.asList(item))) {
            Message message = new Message(MessageKeys.ERR_CANNOT_SELF_CERTIFY, recipient.getDisplayableName());
            errors.add(message.getLocalizedMessage());
            return null;
        }
        
        return recipient;
    }

    private void reassign(Decision decision, List<String> itemIds, Class itemClass)
        throws GeneralException {

        if (null != itemIds) {
            //Validate recipient first to save effort 
            Identity newOwner = getIdentityOrWorkgroup(decision.getRecipient());
            if (newOwner == null) {
                Message message = new Message(Message.Type.Error, MessageKeys.ERR_REASSIGN_NO_RECIPIENT);
                errors.add(message.getLocalizedMessage());
                return;
            }
            Identity me = getDecider();
            
            try {
                // Check for self-certification first to avoid pulling in items if we are just going 
                // to fail anyway.  
                SelfCertificationChecker selfCertChecker = new SelfCertificationChecker(getContext(), certificationId);
                if (selfCertChecker.isSelfCertify(newOwner, itemIds, itemClass)) {
                    throw new SelfCertificationException(newOwner);
                }

                Certification cert = this.getCertification();
                if(cert != null) {
                    if(cert.limitCertReassignment(context)) {
                        throw new LimitReassignmentException();
                    }
                }

                if (CertificationEntity.class.equals(itemClass)) {
                    // We allow for all items in the entity to be reassigned.
                    // Including the read-only items. No read-only check here.
                    cert.bulkReassignEntities(me, itemIds, newOwner, null,
                            this.getDescription(decision), decision.getComments(), false, false);
                } else {
                    // Look through and remove any read only items
                    if (removeReadOnlyItems(cert, itemIds)) {
                        if (this.getBuildWarningsForInvalidItems()) {
                            Message message = new Message(Message.Type.Warn, MessageKeys.WARN_REASSIGN_READONLY_ITEMS_REMOVED);
                            warnings.add(message.getLocalizedMessage());
                        }
                    }

                    cert.bulkReassignItems(me, itemIds, newOwner, null, 
                            this.getDescription(decision), decision.getComments(), false, false);

                }
            }
            catch (SelfCertificationException e) {
                Message message = new Message(Message.Type.Error,
                                                      MessageKeys.ERR_CANNOT_SELF_CERTIFY_REASSIGN,
                                                      e.getSelfCertifier().getDisplayableName());
                errors.add(message.getLocalizedMessage());
            }
            catch (LimitReassignmentException lre) {
                errors.add(lre.getLocalizedMessage());
            }
        }
    }

    /**
     * Remove any of read-only items from item id list that was passed in.
     *
     * @param cert Certification
     * @param itemIds List of certification item ids
     * @return boolean true if any read-only items were removed
     * @throws GeneralException
     */
    private boolean removeReadOnlyItems(Certification cert, List<String> itemIds) throws GeneralException {
        boolean removedReadOnlyItem = false;

        // Check if any items are readonly
        for (Iterator<String> iterator = itemIds.iterator(); iterator.hasNext();) {
            String itemId = iterator.next();
            CertificationItem item = getContext().getObjectById(CertificationItem.class, itemId);
            if (isItemReadOnly(cert, item)) {
                iterator.remove();
                this.invalidItems.add(item.getId());
                removedReadOnlyItem = true;
            }
        }

        return removedReadOnlyItem;
    }

    /**
     * Check if a CertificationItem is read only
     *
     * @param cert Certification
     * @param item CertificationItem
     * @return true if the CertificationItem is read only
     */
    private boolean isItemReadOnly(Certification cert, CertificationItem item) {
        CertificationAction action = item.getAction();

        return CertificationItem.isDecisionLockedByPhase(cert, action, item.getPhase()) ||
                CertificationItem.isDecisionLockedByRevokes(cert, item.getDelegation(),
                        item.getParent().getDelegation(), action);
    }

    void revoke(Decision decision) throws GeneralException {

        if (!item.allowAccountLevelActions())
            return;

        Identity recipient = getIdentityOrWorkgroup(decision.getRecipient());
        if (recipient == null){
            Identity defaultRemediator = item.getDefaultRemediator(context);
            if (defaultRemediator != null)
                recipient = defaultRemediator;
        }

        if (item.isDelegated() && (null == decision.getWorkItemId())) {
            item.revokeDelegation();
        }

        String recipName = recipient != null ? recipient.getName() : null;
        item.revokeAccount(getContext(), getDecider(), decision.getWorkItemId(), getRemediationAction(decision.getStatus()),
                recipName, this.getDescription(decision), decision.getComments());
    }

    void revokeEntityDelegation() {
        item.getParent().revokeDelegation();
    }

    void revokeDelegation() throws GeneralException {
        item.revokeDelegation();

        if (item.getDelegation() != null) {
            // SPECIAL CASE: For line item policy violations, we may have set this delegation on
            // child items in case of remediation. So search for other items that have this delegation set
            // to fully delete it.
            QueryOptions queryOptions = new QueryOptions();
            queryOptions.add(Filter.eq("delegation", item.getDelegation()));
            // Shouldn't be many, so pull the items directly.
            List<CertificationItem> otherItems = this.context.getObjects(CertificationItem.class, queryOptions);
            if (otherItems != null) {
                for (CertificationItem otherItem : otherItems) {
                    otherItem.revokeDelegation();
                    otherItem.markForRefresh();
                }
            }
        }
    }

    /**
     * Performs and undo action that clears a list of certification items
     * @param decision
     */
    void undo(Decision decision) throws GeneralException {
        item.clearDecision(getContext(), getDecider(), decision.getWorkItemId());
    }

    /**
     * Update the provisioning plan with any modifications submitted by
     * the user.
     */
    private ProvisioningPlan getRemediationDetails(Decision decision, RemediationManager.ProvisioningPlanSummary planSummary) throws GeneralException {
        ProvisioningPlan plan =  planSummary.getFullPlan();
        if (decision.getRemediationDetails() != null && !decision.getRemediationDetails().isEmpty() &&
                plan != null){
            for(LineItem lineItem : decision.getRemediationDetails()){
                ProvisioningPlan.Operation op = ProvisioningPlan.Operation.valueOf(lineItem.getNewOperation());

                // if the operation is Set (or Add, in case of editing existing decision), the user has chosen 
                // to modify a value rather than revoke it.  Leave the original revoke request alone and add 
                // a new operation to Set the new value.
                if (ProvisioningPlan.Operation.Set.equals(op) || ProvisioningPlan.Operation.Add.equals(op)){
                    if (Util.isNullOrEmpty(lineItem.getNewValue())) {
                        //Shouldn't happen, we block empty Modify values in the UI, but just in case.
                        throw new GeneralException(
                                new Message(Message.Type.Error, MessageKeys.ERR_MISSING_NEW_VALUE_MODIFY_REMEDIATION));
                    }
                    ProvisioningPlan.GenericRequest newReq = null;
                    if (lineItem.getAttribute() != null){
                        ProvisioningPlan.AttributeRequest attrRequest = new ProvisioningPlan.AttributeRequest();
                        attrRequest.setName(lineItem.getAttribute());
                        attrRequest.setOperation(ProvisioningPlan.Operation.Add);
                        attrRequest.setValue(lineItem.getNewValue());
                        newReq = attrRequest;

                    } else if (lineItem.getPermissionTarget() != null){
                        ProvisioningPlan.PermissionRequest permRequest = new ProvisioningPlan.PermissionRequest();
                        permRequest.setTarget(lineItem.getPermissionTarget());
                        permRequest.setOperation(ProvisioningPlan.Operation.Add);
                        permRequest.setRights(lineItem.getNewValue());
                        newReq = permRequest;
                    }
                    
                    if (newReq != null) {
                        String app = lineItem.getApplication();
                        String instance = lineItem.getInstance();
                        String nativeIdentity = lineItem.getNativeIdentity();
                        ProvisioningPlan.AccountRequest accountReq =
                                plan.getAccountRequest(app, instance, nativeIdentity);
                        if (accountReq != null) {
                            newReq.setTrackingId(plan.getTrackingId());
                            accountReq.add(newReq);
                        }
                    }
                }
            }
        }

        return plan;
    }

    /**
     * Returns the description for the work item associated with this decision
     * @return
     */

    private String getDescription(Decision decision) throws GeneralException {
        return getDescription(decision, null);
    }
    
    private String getDescription(Decision decision, CertificationEntity entity) throws GeneralException{

        if (decision.getDescription() != null && decision.getDescription().trim().length() > 0)
            return decision.getDescription();

        CertificationAction.Status status = CertificationAction.Status.valueOf(decision.getStatus());

        //Use passed in entity if available, otherwise look for the one from the item
        CertificationEntity currEntity = null;
        if (entity != null) {
           currEntity = entity;
        } else if (item != null) {
            currEntity = item.getParent();
        }
        
        //Cant do anything with no entity, is there any case?
        if (currEntity == null) {
            return null;
        }

        if (CertificationAction.Status.Delegated.equals(status)) {
            CertificationActionDescriber describer = null;

            // If this is a bulk entity delegation, get the description without regard to cert item.
            if((item == null) || (decision.getSelectionCriteria().getSelections().size() > 1)) {
                describer = new CertificationActionDescriber(status, getContext());
            } else {
                describer = new CertificationActionDescriber(item, status, getContext());
            }

            return describer.getDefaultDelegationDescription(currEntity);
        } else if (CertificationAction.Status.Remediated.equals(status) ||
                CertificationAction.Status.RevokeAccount.equals(status)) {
            CertificationActionDescriber describer = new CertificationActionDescriber(item, status, getContext());
            return describer.getDefaultRemediationDescription(null, currEntity);
        }

        return null;
    }

    private CertificationAction.RemediationAction getRemediationAction(String status) throws GeneralException{

        if (item!=null) {
            RemediationManager remediationMgr = new RemediationManager(getContext());
            RemediationManager.ProvisioningPlanSummary plan = remediationMgr.calculateRemediationDetails(item, Status.valueOf(status));
            return plan.getAction();
        }
        return null;
    }

    /**
     * Grabs the certification item or certification entity ids that have been decided upon.
     *
     * @param decision
     * @param itemType Class we are certifying, ie CertificationItem or CertificationEntity
     * @return
     */
    List<String> getObjectIds(Decision decision, Class itemType, boolean isNullClearedActions)
            throws GeneralException {
        List<String> itemIds = null;
        String filterParm = CertificationItem.class.equals(itemType) ?
            "parent.certification.id" : "certification.id";

        //IIQETN-5076 :- if isNullClearedActions is true means that we have to set up "null" in
        //spt_certification_item.action field, but the current filter contains the previous status
        //instead of the new one. The new status at this point should be "Open".
        if (isNullClearedActions && !Util.isNullOrEmpty(decision.getSelectionCriteria().getFilter())) {
            String filter = decision.getSelectionCriteria().getFilter();
            filter = filter.replaceFirst("(summaryStatus == \"\\w+\")",
                    "summaryStatus == \"" + AbstractCertificationItem.Status.Open.toString() + "\"");
            filter += " && action.notNull()";
            decision.getSelectionCriteria().setFilter(filter);
        }

        Filter filter = Filter.eq(filterParm, this.certificationId);
        if (!Util.isNullOrEmpty(decision.getSelectionCriteria().getFilter())){
            Filter compiledFilter = Filter.compile(decision.getSelectionCriteria().getFilter());
            filter = Filter.and(filter, compiledFilter);
        }
        
        if (STATUS_ACCOUNT_REASSIGN.equals(decision.getStatus())) {
            String id = decision.getSelectionCriteria().getSelections().get(0);

            itemIds = new ArrayList<>();
            itemIds.add(id);

            item = getContext().getObjectById(CertificationItem.class, id);
            List<CertificationItem> otherItems = item.getParent().getItemsOnSameAccount(item);
            if (!otherItems.isEmpty()) {
                for (CertificationItem item : otherItems) {
                    itemIds.add(item.getId());
                }
            }
        }
        else if (!decision.getSelectionCriteria().isSelectAll()) {
            itemIds = decision.getSelectionCriteria().getSelections();
        }
        else {
            itemIds = this.getItemIdsFromFilter(filter, itemType, decision.getSelectionCriteria().getExclusions());
        }

        // iterate over the selectionModel groups
        List<String> criteriaGroupExclusions = new ArrayList<>();
        List<String> groupItemIds = new ArrayList<>();
        if (!Util.isEmpty(decision.criteriaGroup)) {
            List<Filter> groupFilters = new ArrayList<>();
            for (SelectionCriteria criteria : decision.criteriaGroup) {
                if (!criteria.isSelectAll()) {
                    groupItemIds.addAll(criteria.getSelections());
                }
                else {
                    if (!Util.isNullOrEmpty(criteria.getFilter())) {
                        Filter compiledFilter = Filter.compile(criteria.getFilter());
                        groupFilters.add(compiledFilter);
                    }
                    criteriaGroupExclusions.addAll(criteria.getExclusions());
                }
            }
            // get all the group exclusions
            if (!Util.isEmpty(groupFilters)) {
                Filter groupCriteriaFilter = Filter.and(filter, Filter.or(groupFilters));
                groupItemIds = this.getItemIdsFromFilter(groupCriteriaFilter, itemType, criteriaGroupExclusions);
            }
        }
        if (itemIds == null) {
            itemIds = new ArrayList<>();
        }

        // get the final list of itemIds
        if (!decision.getSelectionCriteria().isSelectAll()) {
            // first remove the duplicates and then add the itemIds
            itemIds.removeAll(groupItemIds);
            itemIds.addAll(groupItemIds);
        }
        else {
            if (!Util.isEmpty(groupItemIds)) {
                Iterator<String> itemIdIterator = itemIds.iterator();
                while (itemIdIterator.hasNext()) {
                    String itemId = itemIdIterator.next();
                    // If the itemId is part of the group exclusion list then remove it
                    if (groupItemIds.contains(itemId)) {
                        itemIdIterator.remove();
                    }
                }
            }
        }

        checkSelfCertify(itemIds, itemType, decision.getSelectionCriteria().isBulk());

        return itemIds;
    }

    private List<String> getItemIds(Decision decision, List<String> entityIds) 
            throws GeneralException {
        List<String> itemIds = null;
        // Since we use an IN query, need to limit how many entities we 
        // process at once
        if (!Util.isEmpty(entityIds) && entityIds.size() > MAX_IN_QUERY_SIZE) {
            itemIds = new ArrayList<>();
            for (int iFrom = 0; iFrom < entityIds.size();) {
                int iTo = ((iFrom + MAX_IN_QUERY_SIZE) > entityIds.size()) ? entityIds.size() : iFrom + MAX_IN_QUERY_SIZE;
                List<String> subsetIds = getItemIdsInternal(decision, entityIds.subList(iFrom, iTo));
                if (!Util.isEmpty(subsetIds)) {
                    itemIds.addAll(subsetIds);
                }
                iFrom = iFrom + MAX_IN_QUERY_SIZE;
            }
        } else {
            itemIds = getItemIdsInternal(decision, entityIds);
        }

        return itemIds;
    }
    
    private List<String> getItemIdsInternal(Decision decision, List<String> entityIds) 
            throws GeneralException {
        List<String> itemIds = new ArrayList<>();

        if (!Util.isEmpty(entityIds)) {
            // build a query to get all items for the selected set of Entities
            QueryOptions itemOps = new QueryOptions();
            itemOps.add(Filter.in("parent.id", entityIds));

            if (isDecisionStatusUndo(decision.getStatus())){
                if (decision.isRevokeDelegation()){
                    itemOps.add(Filter.or(Filter.notnull("action"),
                            Filter.and(Filter.notnull("delegation"), Filter.eq("delegation.revoked", false),
                                    Filter.isnull("delegation.completionState"))));
                } else {
                    itemOps.add(Filter.notnull("action"));
                }
            } else {
                // Dont overwrite active delegations
                itemOps.add(Filter.or(Filter.isnull("delegation"), Filter.eq("delegation.revoked", true),
                        Filter.notnull("delegation.completionState")));
            }

            List<String> exclusions =
                (null != decision.getSelectionCriteria()) ? decision.getSelectionCriteria().getExclusions() : null;

            // TODO: Add support for entities selectionModel groups.

            Iterator<Object[]> rows = getContext().search(CertificationItem.class, itemOps, Arrays.asList("id"));
            while(rows.hasNext()) {
                Object[] row = rows.next();
                String id = (String)row[0];

                if ((null == exclusions) || !exclusions.contains(id)) {
                    itemIds.add(id);
                }
            }
        }

        checkSelfCertify(itemIds, CertificationItem.class, decision.getSelectionCriteria().isBulk());

        return itemIds;
    }

    private void checkSelfCertify(List<String> ids, Class itemType, boolean isBulk)
            throws GeneralException {

        if (Util.isEmpty(ids)) {
            return;
        }

        SelfCertificationChecker selfCertificationChecker = new SelfCertificationChecker(getContext(), certificationId);
        //This checks the system config option against decider capabilities and rights
        if (!selfCertificationChecker.isSelfCertifyAllowed(this.decider)) {
            List<String> selfCertIds = 
                    selfCertificationChecker.getIdentityTargetIds(this.decider, ids, itemType);
            
            if (!Util.isEmpty(selfCertIds)) {
                // Remove self certification ids from the list and add error message
                ids.removeAll(selfCertIds);
                Message message;
                if (isBulk) {
                    message = new Message(MessageKeys.ERR_CANNOT_SELF_CERTIFY_MULTIPLE, 
                            selfCertIds.size(), this.decider.getDisplayableName());
                } else {
                    message = new Message(MessageKeys.ERR_CANNOT_SELF_CERTIFY, 
                            this.decider.getDisplayableName());
                }
                errors.add(message.getLocalizedMessage());
            }
        }
    }

    
    /**
     * Given a decision on a set of CertificationItems, this method
     * will return the distinct entity IDs associated with those items.
     */
    List<String> getDistinctEntities(Decision decision) throws GeneralException{
        List<String> entityIds = new ArrayList<>();

        List<Filter> filters = new ArrayList<>();
        if(!decision.getSelectionCriteria().isSelectAll()) {
            if (!Util.isEmpty(decision.getSelectionCriteria().getSelections())) {
                // this handles cases where we either have a list of entities, or a list of items
                List<String> selections = decision.getSelectionCriteria().getSelections();
                // Since we use an IN query, limit how many entities we process at once
                if (selections.size() > MAX_IN_QUERY_SIZE) {
                    for (int iFrom = 0; iFrom < selections.size();) {
                        int iTo = ((iFrom + MAX_IN_QUERY_SIZE) > selections.size()) ? selections.size() : iFrom + MAX_IN_QUERY_SIZE;
                        List<String> subSelections = selections.subList(iFrom, iTo);
                        filters.add(getSelectionsFilter(subSelections));
                        List<String> subResults = getDistinctEntitiesInner(filters,false);
                        if (!Util.isEmpty(subResults)) {
                            entityIds.addAll(subResults);
                        }
                        iFrom = iFrom + MAX_IN_QUERY_SIZE;
                        filters.clear();
                    }
                } else {
                    filters.add(getSelectionsFilter(selections));
                    entityIds = getDistinctEntitiesInner(filters,false);
                }
            }
        } else {
            if (!Util.isNullOrEmpty(decision.getSelectionCriteria().getFilter())){
                Filter compiledFilter = Filter.compile(decision.getSelectionCriteria().getFilter());
                filters.add(compiledFilter);
            }

            if (!Util.isEmpty(decision.getSelectionCriteria().getExclusions())) {
                filters.add(Filter.not(Filter.in("id", decision.getSelectionCriteria().getExclusions())));
            }

            //IIQETN-5076 :- at this point we already know that SelectionCriteria.selectAll == true and
            //since the SelectionCriteria.filter is always going to be null (it is going to be always null
            //because we have made many changes and the filter should be taken from certificationListBeanParam.filter.filter
            //but that bean no longer exist)
            entityIds = getDistinctEntitiesInner(filters,true);
        }
        
        return entityIds;
    }
    
    private Filter getSelectionsFilter(List<String> selections) {
        return Filter.or(Filter.in("id", selections),
                Filter.in("parent.id", selections));
    }
    
    List<String> getDistinctEntitiesInner(List<Filter> filters, boolean selecAll) throws GeneralException {
        List<String> entityIds = new ArrayList<>();
        //IIQETN-5076 :- if we know that selectAll == true, we have to allow to enter to the condition and
        //use the filter parent.certification.id (it will select all the certification items)
        if (filters.size() > 0 || selecAll) {
            filters.add(Filter.eq("parent.certification.id", this.certificationId));

            QueryOptions ops = new QueryOptions();
            ops.add(Filter.and(filters));
            ops.setDistinct(true);

            Iterator<Object[]> rows = context.search(CertificationItem.class, ops, Arrays.asList("parent.id"));
            while (rows.hasNext()) {
                entityIds.add((String)rows.next()[0]);
            }
        }

        return entityIds;
    }


    /**
     * Takes a provided filter and returns a list of ids for the given item type.
     */
    private List<String> getItemIdsFromFilter(Filter filter, Class itemType, List<String> exclusions) {
        List<String> itemIds = new ArrayList<>();

        if(filter!=null) {
            QueryOptions qo = new QueryOptions();
            qo.add(filter);
            try {
                Iterator<Object[]> rows = getContext().search(itemType, qo, Arrays.asList("id"));
                while(rows.hasNext()) {
                    Object[] row = rows.next();
                    String id = (String)row[0];
                    if(exclusions==null || !exclusions.contains(id)) {
                        itemIds.add(id);
                    }
                }
            } catch(GeneralException ge) {
                if (log.isWarnEnabled())
                    log.warn("Unable to fetch certification ids for filter [" + 
                             filter + "]: " + ge.getMessage(), ge);
            }
        }

        return itemIds;
    }

    private void addInvalidItem(List<CertificationItem> items, CertificationAction.Status status) {
        /** Build warnings **/
        for (CertificationItem item : items) {

            // Don't display warnings if the status doesn't apply to the item
            if (!actionApplies(item, status)) {
                continue;
            }
            if (this.getBuildWarningsForInvalidItems()) {
                Message msg = new Message(Message.Type.Warn, MessageKeys.ERR_CANT_BLK_CERT_IDENT,
                         item.getErrorDescription(), item.getParent().getFullname());
                String msgText = msg.getLocalizedMessage();
    
                // Make sure we don't overwhelm the list with a lot of duplicate msgs
                if (!warnings.contains(msgText)){
                    warnings.add(msgText);
                }
            }

            // add the ID so we can pass it back to the UI
            this.invalidItems.add(item.getId());
        }

    }
    
    /**
     * Gets whether or not the specified CertificationAction applies to a CertificationItem.
     * @param item The CertificationItem to test.
     * @param status The CertificationItem status.
     * @return True if the action applies to the CertificationItem, false otherwise.
     */
    private static boolean actionApplies(CertificationItem item, CertificationAction.Status status)
    {
        assert(item != null);
        
        if (item.getType() == CertificationItem.Type.PolicyViolation) {
            if (status == CertificationAction.Status.Approved) {
                return false;
            }
        }
        
        return true;
    }

    

    DecisionResults buildSummary(List<Decision> decisions, boolean simpleResult) throws GeneralException{

        DecisionResults summary = new DecisionResults();

        summary.errors = errors;
        summary.warnings = warnings;
        summary.status = getSummaryStatus(summary);
        summary.invalidItems = this.invalidItems;

        if (!simpleResult) {
            boolean workItemComplete = false;
            if (!decisions.isEmpty()) {
                String workItemId = decisions.get(0).getWorkItemId();

                if (workItemId != null && workItemId.length() > 0) {
                    WorkItem workItem = context.getObjectById(WorkItem.class, workItemId);
                    if (workItem != null) {
                        workItemComplete = isComplete(workItem);
                    }
                }
            }

            Certification cert = getContext().getObjectById(Certification.class, certificationId);

            if (getCertification() != null) {
                summary.completedItems = cert.getCompletedItems();
                summary.totalItems = cert.getTotalItems();
                summary.certifiedItems = cert.getCertifiedItems();
                summary.certificationRequiredItems = cert.getCertificationRequiredItems();
                summary.overdueItems = cert.getOverdueItems();
                summary.percentage = Math.round(Util.getPercentage(summary.completedItems, summary.totalItems));
                summary.completedEntities = cert.getCompletedEntities();
                summary.totalEntities = cert.getTotalEntities();
                summary.entityPercentage = Math.round(Util.getPercentage(summary.completedEntities, summary.totalEntities));

                QueryOptions ops = new QueryOptions();
                ops.add(Filter.eq("parent.certification.id", certificationId));
                ops.add(Filter.and(Filter.notnull("delegation"), Filter.isnull("delegation.completionState"),
                        Filter.eq("delegation.revoked", false)));
                ops.setDistinct(true);

                summary.activeDelegations = getContext().countObjects(CertificationItem.class, ops);

                /** Determine if it is ready for signoff **/
                if (cert.isComplete()) {
                    try {
                        CertificationService svc = new CertificationService(getContext());
                        summary.readyForSignoff = svc.isReadyForSignOff(cert);
                    } catch (GeneralException ge) {
                        if (log.isWarnEnabled())
                            log.warn("Unable to determine if the certification is ready for signoff: " +
                                    ge.getMessage(), ge);
                    }
                }
            }
            summary.workItemComplete = workItemComplete;
        }
        
        return summary;
    }

    /**
     * Get summary status based on errors, warnings.
     *
     * @param results DecisionResults
     * @return status string
     */
    private String getSummaryStatus(DecisionResults results) {
        if (!Util.isEmpty(results.errors)) {
            return "error";
        }
        else if (!Util.isEmpty(results.warnings)) {
            return "warning";
        }
        return "success";
    }

    /**
     * Saves the certification object and pushes all the decisions 
     * @return
     * @throws GeneralException
     */
    private void saveAndRefreshCertification() throws GeneralException {
        Certification cert = getContext().getObjectById(Certification.class, certificationId);
        if(cert!=null) {
            Certificationer certificationer = new Certificationer(getContext());
            List<Message> refreshMessages = certificationer.refresh(cert, true, false);
            if (!Util.isEmpty(refreshMessages)) {
                for (Message msg : refreshMessages) {
                    if (msg.isError()) {
                        errors.add(msg.getLocalizedMessage());
                    } else if (msg.isWarning()) {
                        warnings.add(msg.getLocalizedMessage());
                    }
                }
            }
        }
    }

    private Identity getIdentityOrWorkgroup(String idOrName) throws GeneralException{
        return ObjectUtil.getIdentityOrWorkgroup(context, idOrName);
    }
    
    
    /** 
     * Null out any cert actions associated with this decision that have a
     * status of CLEARED.
     * 
     * @param decision
     * @throws GeneralException 
     */
    private void nullClearedActions(Decision decision) throws GeneralException {

        //CYA - shouldn't happen, but just in case
        if (!isDecisionStatusUndo(decision.getStatus()))
            return;

        // get the list of cert item ids, depending on the type of decision
        List<String> itemIds;
        if (decision.isEntityDecision())             
            itemIds = getItemIds(decision, getDistinctEntities(decision));
        else 
            itemIds = getObjectIds(decision, CertificationItem.class, true);

        int cnt = 0;        
        CertificationItem item;
        for (String itemId : itemIds) {
            item = getContext().getObjectById(CertificationItem.class, itemId);
            if ((item != null) && (item.isCleared())){
                context.removeObject(item.getAction());
                item.setAction(null);
            }
            
            cnt++;
            if (cnt % 20 == 0){
                this.commitAndDecache();
            }
        }

        this.commitAndDecache();
    }

    /**
     * Commit the transaction, decache, and reattach as necessary 
     * @throws GeneralException
     */
    private void commitAndDecache() throws GeneralException {
        getContext().commitTransaction();
        getContext().decache();
        // Reattach decider to avoid lazy initialization errors 
        this.decider = ObjectUtil.reattach(getContext(), this.decider);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Getters/Setters
    //
    //////////////////////////////////////////////////////////////////////  

    public SailPointContext getContext() {
        return context;
    }


    public void setContext(SailPointContext context) {
        this.context = context;
    }


    public Certification getCertification() {
        Certification certification = null;
        if (this.certification != null) {
            certification = this.certification;
        }
        else if (certificationId != null) {
            try {
                certification = getContext().getObjectById(Certification.class, certificationId);
            } catch(GeneralException ge) {
                if (log.isWarnEnabled())
                    log.warn("Exception while loading certification with id [" + 
                             certificationId + "]: " + ge.getMessage(), ge);
            }
        }
        return certification;
    }


    public List<String> getErrors() {
        return errors;
    }


    public void setErrors(List<String> errors) {
        this.errors = errors;
    }


    public List<String> getWarnings() {
        return warnings;
    }


    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public Identity getDecider() {
        return decider;
    }

    public void setDecider(Identity decider) {
        this.decider = decider;
    }

    public CertificationItem getItem() {
        return item;
    }


    public void setItem(CertificationItem item) {
        this.item = item;
    }
    //////////////////////////////////////////////////////////////////////
    //
    // Inner Classes
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Inner class - Decision model to match web/sailpoint/model/Decision.js
     * @author peter.holcomb
     *
     */
    public static class Decision extends BaseDecision {

        public static final String CERTIFICATION_ITEM = "certificationItemId";
        public static final String SELECTION_MODEL = "selectionModel";
        public static final String DELEGATION_REVIEW_ACTION = "delegationReviewAction";
        public static final String CHALLENGE_COMMENTS = "challengeComments";
        public static final String ONE_STEP_CHALLENGE = "oneStepChallenge";
        public static final String CHALLENGE_ACTION = "challengeAction";
        public static final String REVOKE_DELEGATION = "revokeDelegation";
        public static final String ENTITY_DECISION = "entityDecision";

        String itemType;
        String workItemId;
        SelectionCriteria selectionCriteria;
        List<SelectionCriteria> criteriaGroup;
        boolean addRoles;
        boolean mitigationExpiresNextCert;
        Map<String,String> custom;
        String custom1;
        String custom2;
        boolean overwriteCustomFields;
        boolean delegationReview;
        boolean revokeDelegation;
        boolean revokeEntityDelegation;
        boolean entityDecision;
        String decisionScope;
        String delegationReviewAction;

        boolean provisionMissingRoles;

        String challengeAction;
        String challengeComments;
        String bundleAssignmentId;

        /**
         * Indicate that this decision is a one-step challenge change.
         */
        boolean oneStepChallenge = false;

        public Decision() {}

        public Decision(String status, SelectionCriteria selectionCriteria) {
            this.setStatus(status);
            this.selectionCriteria = selectionCriteria;
        }
        
        @SuppressWarnings("unchecked")
        public Decision(Map<String, Object> decisionMap, ListFilterService listFilterService,
                        Certification.Type certificationType) throws GeneralException {
            super(decisionMap);

            if (decisionMap.containsKey(DELEGATION_REVIEW_ACTION)) {
                this.delegationReviewAction = (String)decisionMap.get(DELEGATION_REVIEW_ACTION);
            }

            if (decisionMap.containsKey(CHALLENGE_COMMENTS)) {
                this.challengeComments = (String)decisionMap.get(CHALLENGE_COMMENTS);
            }

            if (decisionMap.containsKey(ONE_STEP_CHALLENGE)) {
                this.setOneStepChallenge((Boolean)decisionMap.get(ONE_STEP_CHALLENGE));
            }

            if (decisionMap.containsKey(CHALLENGE_ACTION)) {
                this.challengeAction = (String)decisionMap.get(CHALLENGE_ACTION);
            }

            if (decisionMap.containsKey(REVOKE_DELEGATION)) {
                this.setRevokeDelegation((Boolean)decisionMap.get(REVOKE_DELEGATION));
            }

            if (decisionMap.containsKey(ENTITY_DECISION)) {
                this.setEntityDecision((Boolean)decisionMap.get(ENTITY_DECISION));
            }

            if (decisionMap.containsKey(CERTIFICATION_ITEM)) {
                List<String> strList = new ArrayList<>();
                strList.add((String)decisionMap.get(CERTIFICATION_ITEM));
                this.selectionCriteria = new SelectionCriteria(strList);
            }

            else if (decisionMap.containsKey(SELECTION_MODEL)) {
                SelectionModel selectionModel = new SelectionModel((Map<String, Object>)decisionMap.get(SELECTION_MODEL)); 
                this.selectionCriteria = this.createSelectionCriteria(certificationType, selectionModel, listFilterService);
                // get any selectionModel groups
                if (!Util.isEmpty(selectionModel.getGroups())) {
                    this.criteriaGroup = new ArrayList<>();
                    for (SelectionModel model : selectionModel.getGroups()) {
                        SelectionCriteria criteria = this.createSelectionCriteria(certificationType, model, listFilterService);
                        this.criteriaGroup.add(criteria);
                    }
                }
            }
            else {
                throw new GeneralException("Decision must have either certificationItemId or selectionModel defined");
            }
        }

        public boolean isPriorityDecision(){
            return STATUS_UNDO_DELEGATION.equals(this.getStatus()) ||
                    STATUS_DELEGATION_REVIEW_REJECT.equals(this.getStatus()) ||
                    STATUS_DELEGATION_REVIEW_ACCEPT.equals(this.getStatus());
        }

        public String getItemType() {
            return itemType;
        }
        public void setItemType(String itemType) {
            this.itemType = itemType;
        }
        public SelectionCriteria getSelectionCriteria() {
            return selectionCriteria;
        }
        public void setSelectionCriteria(SelectionCriteria selectionCriteria) {
            this.selectionCriteria = selectionCriteria;
        }
        public List<SelectionCriteria> getCriteriaGroup() {
            return criteriaGroup;
        }
        public void setCriteriaGroup(List<SelectionCriteria> criteriaGroup) {
            this.criteriaGroup = criteriaGroup;
        }
        public boolean isAddRoles() {
            return addRoles;
        }
        public void setAddRoles(boolean addRoles) {
            this.addRoles = addRoles;
        }
        public String getWorkItemId() {
            return workItemId;
        }
        public void setWorkItemId(String workItemId) {
            this.workItemId = workItemId;
        }
        public boolean isMitigationExpiresNextCert() {
            return mitigationExpiresNextCert;
        }
        public void setMitigationExpiresNextCert(boolean mitigationExpiresNextCert) {
            this.mitigationExpiresNextCert = mitigationExpiresNextCert;
        }
        public Map<String, String> getCustom() {
            return custom;
        }
        public void setCustom(Map<String, String> custom) {
            this.custom = custom;
        }

        public String getCustom1() {
            return custom1;
        }

        public void setCustom1(String custom1) {
            this.custom1 = custom1;
        }

        public String getCustom2() {
            return custom2;
        }

        public void setCustom2(String custom2) {
            this.custom2 = custom2;
        }

        public boolean isOverwriteCustomFields() {
            return overwriteCustomFields;
        }

        public void setOverwriteCustomFields(boolean overwriteCustomFields) {
            this.overwriteCustomFields = overwriteCustomFields;
        }

        public boolean isProvisionMissingRoles() {
            return provisionMissingRoles;
        }

        public void setProvisionMissingRoles(boolean provisionMissingRoles) {
            this.provisionMissingRoles = provisionMissingRoles;
        }

        public boolean isDelegationReview() {
            return delegationReview;
        }

        public void setDelegationReview(boolean delegationReview) {
            this.delegationReview = delegationReview;
        }

        public boolean isEntityDecision() {
            return entityDecision;
        }

        public void setDecisionScope(String decisionScope) {
            this.decisionScope = decisionScope;
        }

        public String getDecisionScope() {
           return this.decisionScope; 
        }
        
        public boolean isDecisionScope(String testScope) {
            return Util.nullSafeEq(this.decisionScope, testScope);
        }

        public void setEntityDecision(boolean entityDecision) {
            this.entityDecision = entityDecision;
        }

        public boolean isRevokeDelegation() {
            return revokeDelegation;
        }

        public void setRevokeDelegation(boolean revokeDelegation) {
            this.revokeDelegation = revokeDelegation;
        }

        public boolean isRevokeEntityDelegation() {
            return revokeEntityDelegation;
        }

        public void setRevokeEntityDelegation(boolean revokeEntityDelegation) {
            this.revokeEntityDelegation = revokeEntityDelegation;
        }

        public String getChallengeAction() {
            return challengeAction;
        }

        public void setChallengeAction(String challengeAction) {
            this.challengeAction = challengeAction;
        }

        public String getChallengeComments() {
            return challengeComments;
        }

        public void setChallengeComments(String challengeComments) {
            this.challengeComments = challengeComments;
        }

        public void setOneStepChallenge(boolean oneStepChallenge) {
            this.oneStepChallenge = oneStepChallenge;
        }

        public boolean isOneStepChallenge() {
            return this.oneStepChallenge;
        }

        public String getBundleAssignmentId() {
            return bundleAssignmentId;
        }

        public void setBundleAssignmentId(String bundleAssignmentId) {
            this.bundleAssignmentId = bundleAssignmentId;
        }

        /**
         * Needed for one step delegation review in the responsive UI.
         *
         * @return the delegation review action
         */
        public String getDelegationReviewAction() {
            return delegationReviewAction;
        }

        public void setDelegationReviewAction(String delegationReviewAction) {
            this.delegationReviewAction = delegationReviewAction;
        }

        /**
         * Construct the selectionCriteria from the selectionModel passed in
         * @param selectionModel SelectionModel object
         * @return SelectionCriteria
         * @throws GeneralException 
         */
        private SelectionCriteria createSelectionCriteria(Certification.Type certificationType,
                SelectionModel selectionModel, ListFilterService listFilterService)
                throws GeneralException {
            SelectionCriteria criteria = new SelectionCriteria(selectionModel);
            List<Filter> filters = new ArrayList<>();
            if (!Util.isEmpty(selectionModel.getFilterValues())) {
                filters.addAll(listFilterService.convertQueryParametersToFilters(selectionModel.getFilterValues(), true));
            }
            if(selectionModel.getQueryString() != null) {
                filters.add(listFilterService.getFilterByQuery(certificationType, selectionModel.getQueryString()));
            }
            if (!Util.isEmpty(filters)) {
                Filter fullFilter = (filters.size() == 1) ? filters.get(0) : Filter.and(filters);
                criteria.setFilter(fullFilter.getExpression());
            }

            return criteria;
        }
    }

    public static class DecisionResults {

        /** Whether the action was a success/failure **/
        String status;
        List<String> warnings;
        List<String> errors;
        List<String> invalidItems;
        int completedItems;
        int totalItems;
        int certifiedItems;
        int certificationRequiredItems;
        int overdueItems;
        int percentage;
        boolean timedOut;

        int completedEntities;
        int totalEntities;
        int entityPercentage;

        int activeDelegations;
        
        boolean workItemComplete;

        boolean readyForSignoff;
        public String getStatus() {
            return status;
        }
        public void setStatus(String status) {
            this.status = status;
        }
        public List<String> getWarnings() {
            return warnings;
        }
        public void setWarnings(List<String> warnings) {
            this.warnings = warnings;
        }
        public List<String> getErrors() {
            return errors;
        }
        public void setErrors(List<String> errors) {
            this.errors = errors;
        }
        public List<String> getInvalidItems() {
            return invalidItems;
        }

        public void setInvalidItems(List<String> invalidItems) {
            this.invalidItems = invalidItems;
        }

        public int getCompletedItems() {
            return completedItems;
        }
        public void setCompletedItems(int completedItems) {
            this.completedItems = completedItems;
        }
        public int getTotalItems() {
            return totalItems;
        }
        public void setTotalItems(int totalItems) {
            this.totalItems = totalItems;
        }
        public int getCertifiedItems() {
            return certifiedItems;
        }
        public void setCertifiedItems(int certifiedItems) {
            this.certifiedItems = certifiedItems;
        }
        public int getCertificationRequiredItems() {
            return certificationRequiredItems;
        }
        public void setCertificationRequiredItems(int certificationRequiredItems) {
            this.certificationRequiredItems = certificationRequiredItems;
        }
        public int getOverdueItems() {
            return overdueItems;
        }
        public void setOverdueItems(int overdueItems) {
            this.overdueItems = overdueItems;
        }
        public int getPercentage() {
            return percentage;
        }
        public void setPercentage(int percentage) {
            this.percentage = percentage;
        }
        public boolean isReadyForSignoff() {
            return readyForSignoff;
        }
        public void setReadyForSignoff(boolean readyForSignoff) {
            this.readyForSignoff = readyForSignoff;
        }
        public boolean isTimedOut() {
            return timedOut;
        }
        public void setTimedOut(boolean val) {
            timedOut = val;
        }

        public int getCompletedEntities() {
            return completedEntities;
        }

        public void setCompletedEntities(int completedEntities) {
            this.completedEntities = completedEntities;
        }

        public int getTotalEntities() {
            return totalEntities;
        }

        public void setTotalEntities(int totalEntities) {
            this.totalEntities = totalEntities;
        }

        public int getEntityPercentage() {
            return entityPercentage;
        }

        public void setEntityPercentage(int entityPercentage) {
            this.entityPercentage = entityPercentage;
        }

        public int getActiveDelegations() {
            return activeDelegations;
        }

        public void setActiveDelegations(int activeDelegations) {
            this.activeDelegations = activeDelegations;
        }
        
        public boolean isWorkItemComplete() {
            return workItemComplete;
        }
        
        public void setWorkItemComplete(boolean workItemComplete) {
            this.workItemComplete = workItemComplete;
        }
    }
}
