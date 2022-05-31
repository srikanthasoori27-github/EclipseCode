/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.view.certification;

import sailpoint.api.SailPointContext;
import sailpoint.api.certification.PolicyViolationCertificationManager;
import sailpoint.api.certification.SelfCertificationChecker;
import sailpoint.object.*;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.view.IdentitySummary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class CertificationItemDecisionColumn extends CertificationItemColumn {

    private CertificationDefinition definition;

    private final static String COL_TYPE = "type";
    private final static String COL_VIOLATION = "policyViolation";
    private final static String COL_EXCEPTION_SNAP = "exceptionEntitlements";
    private final static String COL_CERT_SIGNED = "parent.certification.signed";
    private final static String COL_ENTITY_DATE_COMPLETE = "parent.completed";
    private final static String COL_PHASE = "phase";
    private final static String COL_ACTION = "action";
    private final static String COL_ACTION_REQUIRED = "actionRequired";
    private final static String COL_DELEGATION = "delegation";
    private final static String COL_PARENT_DELEGATION = "parent.delegation";
    private final static String COL_BULK_REASSIGNMENT = "parent.certification.bulkReassignment";

    private int maxWidth = 27;

    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        List<String> cols = super.getProjectionColumns();
        cols.add(COL_TYPE);
        cols.add(COL_VIOLATION);
        cols.add(COL_EXCEPTION_SNAP);
        cols.add(COL_CERT_SIGNED);
        cols.add(COL_ENTITY_DATE_COMPLETE);
        cols.add(COL_PHASE);
        cols.add(COL_ACTION);
        cols.add(COL_ACTION_REQUIRED);
        cols.add(COL_DELEGATION);
        cols.add(COL_PARENT_DELEGATION);
        cols.add(COL_BULK_REASSIGNMENT);
        return cols;
    }


    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException {
        definition = this.getCertificationDefinition(row);

        CertificationAction.Status stat = null;

        CertificationAction action = (CertificationAction) row.get(COL_ACTION);
        CertificationDelegation delegation = (CertificationDelegation) row.get(COL_DELEGATION);
        CertificationDelegation parentDel = (CertificationDelegation) row.get(COL_PARENT_DELEGATION);

        String workItemId = this.getWorkItemId();
        boolean viewingEntityDelegation = workItemId != null && parentDel != null &&
                workItemId.equals(parentDel.getWorkItem());
        boolean viewingItemDelegation = workItemId != null && delegation != null &&
                workItemId.equals(delegation.getWorkItem());

        CertificationDecisionStatus decisions =  getDecisionChoices(getContext().getSailPointContext(), row,
                viewingEntityDelegation || viewingItemDelegation);

        if (action != null) {
            decisions.setDependantDecisions(action.getSourceAction() != null
                    || (action.getParentActions() != null && !action.getParentActions().isEmpty()));
            
            // Get the ID for the source action item
            if (action.getSourceAction() != null) {
                QueryOptions ops = new QueryOptions(Filter.eq("action", action.getSourceAction()));
                ops.setResultLimit(1);
                Iterator<Object[]> items =
                        getSailPointContext().search(CertificationItem.class, ops, COL_ID);
                if (items.hasNext()) {
                    decisions.setSourceItemId((String) items.next()[0]);
                    Util.flushIterator(items);
                }
            }
            
            // Get the display names of the parent action items
            if (!Util.isEmpty(action.getParentActions())) {
                // Use CertificationItemDisplayNameColumn to get the display name
                CertificationItemDisplayNameColumn displayNameColumn = new CertificationItemDisplayNameColumn();
                displayNameColumn.init(getContext(), null);
                List<String> columnList = displayNameColumn.getProjectionColumns();
                QueryOptions ops = new QueryOptions(Filter.in("action", action.getParentActions()));
                Iterator<Object[]> items = getSailPointContext().search(CertificationItem.class, ops, columnList);
                List<Map<String, Object>> results = Util.iteratorToMaps(items, Util.listToCsv(columnList));
                List<String> displayNames = new ArrayList<String>();
                if (!Util.isEmpty(results)) {
                    // Should only be a single result
                    Map<String, Object> result = results.get(0);
                    displayNames.add((String)displayNameColumn.getValue(result));
                }
                decisions.setParentItemDisplayNames(displayNames);
            }
        }

        // If there's an active delegation in play, we show the status as
        // delegated, even if the delgatee has already saved a decision.
        // note that we don't want to show the delegated status if we are viewing the delegation workitem
        if (delegation != null && delegation.isActive() && !viewingItemDelegation) {
            stat = CertificationAction.Status.Delegated;
            String owner = delegation.getOwnerName() ;
            decisions.setOwner(owner);
        } else if (parentDel != null && parentDel.getCompletionState() == null
                   && !viewingEntityDelegation && !viewingItemDelegation) {
            decisions.setEntityDelegation(true);
            stat = CertificationAction.Status.Delegated;
            String owner = parentDel.getOwnerName();
            decisions.setOwner(owner);
        }

        // Always set the delegationOwner so we have a name to display in the revoke dialog - IIQCB-139
        if (delegation != null && delegation.getOwnerName() != null){
            setDelegationFields(decisions, delegation);
        }
        else if (parentDel != null && parentDel.getOwnerName() != null){
            setDelegationFields(decisions, parentDel);
        }

        // If there's no current delegation info to display, check the action
        if (null != action && stat == null) {
            stat = action.getStatus();

            // Use the revoke account pseudo-status for account revokes.
            if (CertificationAction.Status.Remediated.equals(stat) &&
                action.isRevokeAccount()) {
                stat = CertificationAction.Status.RevokeAccount;
            }

            // acknowledges are presented as mitigations in the UI
            if (CertificationAction.Status.Acknowledged.equals(stat)) {
                stat = CertificationAction.Status.Mitigated;
            }

            decisions.setOwner(action.getOwnerName());
        }

        if (parentDel != null){
            decisions.setParentDelegationId(parentDel.getId());
            decisions.setWorkItemId(parentDel.getWorkItem());
        } else if (delegation != null){
            decisions.setWorkItemId(delegation.getWorkItem());
        }

        decisions.setCurrentStateWithStatus(stat);

        // if the certification is signed, make sure that the current state
        // is in the allowed decisions list. Auto-closed cert items may have
        // a decision which is not normally allowed.
        if (stat != null && row.get(COL_CERT_SIGNED) != null && !decisions.hasStatus(stat)){
            decisions.addStatus(stat);
        }

        // calculate max column width
        int decisionCount = decisions.getDecisions() != null ? decisions.getDecisions().size() : 0;
        setWidth(27 * (decisionCount + 1));

        return decisions;
    }

    @Override
    public void afterRender() {
        ColumnConfig col = this.getColumnConfig();
        if (col != null && col.getWidth() < maxWidth){
            col.setWidth(maxWidth);
        }
    }

    //---------------------------------------------------------------
    //
    // Private Methods
    //
    //---------------------------------------------------------------

    /**
     * Set the width value if it's greater than the existing max width
     */
    private void setWidth(int w){
        if (w > maxWidth)
            maxWidth = w;
    }


    /**
     * Calculate whether the decision can be changed or not.
     */
    private boolean calculateCanChangeDecision(Map<String,Object> row)
        throws GeneralException {

        return new Decider(row).decide();
    }

    /**
     * Set the delegation fields of the decision based on the supplied delegation.
     */
    private void setDelegationFields(CertificationDecisionStatus decision, CertificationDelegation delegation)
            throws GeneralException {
        Identity identity = this.getSailPointContext().getObjectByName(Identity.class, delegation.getOwnerName());
        decision.setDelegationComments(delegation.getComments());
        decision.setDelegationDescription(delegation.getDescription());
        if (identity != null) {
            decision.setDelegationOwner(new IdentitySummary(identity));
        }
    }

    /**
     * 
     * Encapsulate logic to decide if a
     * decision can be made on a row. I have tried to refactor the logic
     * but it is still pretty ugly. There should be unit tests.
     */
    private class Decider {
        
        private Map<String, Object> row;
        private Role role;

        private CertificationAction action;
        private CertificationDelegation delegation;
        private CertificationDelegation parentDelegation;
        private Date entityCompletionDate;
        private boolean itemDelegated;
        private boolean entityDelegated;
        private boolean itemReturned;
        private boolean viewingEntityDelegation;
        private boolean viewingItemDelegation;
        
        Decider(Map<String, Object> row) {
            this.row = row;
        }
        
        private boolean decide() throws GeneralException {

            // Signed certifications are not editable
            Date signDate = (Date) row.get(COL_CERT_SIGNED);
            if (signDate != null) {
                return false;
            }

            SelfCertificationChecker selfCertificationChecker = new SelfCertificationChecker(getSailPointContext(), (String)row.get(COL_CERT_ID));
            if (selfCertificationChecker.isSelfCertify(getContext().getUserContext().getLoggedInUser(), Collections.singletonList((String)row.get(COL_ID)), CertificationItem.class)) {
                return false;
            }
            
            role = getCertRole(row);

            // Trying to do this as much as possible with projection query
            action = (CertificationAction)row.get(COL_ACTION);
            delegation = (CertificationDelegation) row.get(COL_DELEGATION);
            parentDelegation = (CertificationDelegation) row.get(COL_PARENT_DELEGATION);
            entityCompletionDate = (Date) row.get(COL_ENTITY_DATE_COMPLETE);

            itemDelegated = delegation != null && delegation.isActive() && !delegation.isRevoked();
            entityDelegated = parentDelegation != null && entityCompletionDate == null && parentDelegation.isActive();
            itemReturned = delegation != null && delegation.isReturned();

            String workItemId = getWorkItemId();
            viewingEntityDelegation = workItemId != null && parentDelegation != null && workItemId.equals(parentDelegation.getWorkItem());
            viewingItemDelegation = workItemId != null && delegation != null && workItemId.equals(delegation.getWorkItem());

            if (isCertItemLockedByPhaseOrRevokes()) {
                return false;
            }
            
            // Case 0 - Neither identity or item is delegated, not read-only if
            // certifier is logged in.
            if (!itemDelegated && !entityDelegated) {
                return role.isCertificationOwner;
            }

            // Case 1 - Item is delegated, identity is not.
            //  Editable if delegation owner is the logged in user or if the
            //  person that delegated the item is the logged in user (this
            //  allows the person that delegated the item to revoke it).
            if (itemDelegated && !entityDelegated) {
                if (role.isItemDelegationOwner ||
                    (role.isItemDelegationRequester && !role.isViewingItemWorkItem) ||
                    (role.isCertificationOwner && role.isCertifierItemDelegationRequester &&
                     role.isViewingCertification)) {
                    return true;
                } else {
                    return false;
                }
            }

            // Case 2 - Identity is delegated, item is not.
            if (entityDelegated && !itemDelegated) {
                return calculateCanChangeDecisionWhenEntityDelegatedAndItemNotDelegated();
            }

            // Case 3 - Identity and item are delegated.
            if (entityDelegated && itemDelegated) {
                return calculateCanChangeDecisionWhenEntityAndItemAreDelegated();
            }

            return true;
        }

        private boolean isCertItemLockedByPhaseOrRevokes() throws GeneralException {

            Certification cert = getCertification(row);
            Certification.Phase itemPhase = (Certification.Phase) row.get(COL_PHASE);

            if  (CertificationItem.isDecisionLockedByPhase(cert, action, itemPhase) ||
                   CertificationItem.isDecisionLockedByRevokes(cert, delegation, parentDelegation, action)){
                return true;
            }
            
            return false;
        }
        

        //  TQM: very complicated logic. Where are the unit tests?
        //
        //  If delegation owner is logged in, item is editable if the action
        //  has not been set OR if the logged in user made the action decision.
        //
        //  If delegation owner is not logged in, item is only editable if
        //  the certification owner is looking at a returned item in
        //  the certification report.
        private boolean calculateCanChangeDecisionWhenEntityDelegatedAndItemNotDelegated() {

            boolean isReturnedItemRequester =
                itemReturned && role.isItemDelegationRequester;

            if (viewingEntityDelegation && role.isIdentityDelegationOwner) {
                return !role.wasItemDecidedOutsideOfIdentityDelegation;
            }
            
            if (role.isIdentityDelegationOwner) {
                if ((isReturnedItemRequester || !itemReturned) &&
                    ((null == action) || role.isItemActionActor)) {
                    return true;
                } else if (!role.wasItemDecidedOutsideOfIdentityDelegation) {
                    return true;
                } else if (role.isCertificationOwner && role.isViewingCertification &&
                         role.wasItemDecidedOutsideOfIdentityDelegation) {
                    return true;
                } else { 
                    return false;
                }
            } else {
                return isReturnedItemRequester && role.isViewingCertification;
            }
        }

        //  TQM: very complicated logic. Where are the unit tests?
        //
        //  If the identity delegation owner is the logged in user, the
        //  item delegation is only editable if the logged in user delegated
        //  the item.
        //
        //  If the item delegation owner is the logged in user, the item is
        //  editable.
        //
        //  If the person that delegated the item is the logged in user, the
        //  delegation should be editable.
        private boolean calculateCanChangeDecisionWhenEntityAndItemAreDelegated() {

            if (viewingItemDelegation) {
                return role.isItemDelegationOwner;
            } else if (viewingEntityDelegation) {
                return role.isIdentityDelegationOwner || role.isItemDelegationRequester;
            } else if (role.isIdentityDelegationOwner) {
                if (role.isItemDelegationRequester) {
                    return true;
                } else {
                    return false;
                }
            } else if (role.isItemDelegationRequester) {
                return true;
            } else {
                return false;
            }
        }
    }

    private CertificationDecisionStatus getDecisionChoices(
        SailPointContext context,
        Map<String, Object> row,
        boolean isViewingDelegation) throws GeneralException {

        CertificationDecisionStatus decisions = null;

        CertificationItem.Type type = (CertificationItem.Type)row.get(COL_TYPE);

        CertificationDelegation delegation = (CertificationDelegation) row.get(COL_DELEGATION);
        String workItem = this.getContext().getBuilderAttributes().getString(BUILDER_ATTR_WORKITEM_ID);
        boolean inLineItemDelegation = delegation != null &&  workItem != null && workItem.equals(delegation.getWorkItem());
        boolean isBulkReassignment = (Boolean)row.get(COL_BULK_REASSIGNMENT);
        if (CertificationItem.Type.PolicyViolation.equals(type)) {
            PolicyViolation violation = (PolicyViolation)row.get(COL_VIOLATION);
            decisions = PolicyViolationCertificationManager.getViolationDecisionChoices(
                context,
                violation,
                !inLineItemDelegation && definition.isAllowItemDelegation(context));
        } else {
            EntitlementSnapshot snap = (EntitlementSnapshot)row.get(COL_EXCEPTION_SNAP);
            String appName = (null != snap) ? snap.getApplication() : null;
            decisions = initEntitlementDecisionChoices(context, getCertificationType(row), type,
                    appName, inLineItemDelegation, isBulkReassignment, isViewingDelegation);
        }

        decisions.setActionRequired(Util.otob(row.get(COL_ACTION_REQUIRED)));
        decisions.setCanChangeDecision(calculateCanChangeDecision(row));

        return decisions;
    }

    private CertificationDecisionStatus initEntitlementDecisionChoices(SailPointContext context,
                                                                        Certification.Type certType,
                                                                        CertificationItem.Type type,
                                                                        String appName, 
                                                                        boolean isLineItemDelegation,
                                                                        boolean isBulkReassignment,
                                                                        boolean isViewingDelegation)
            throws GeneralException {

        CertificationDecisionStatus decisions = new CertificationDecisionStatus();

        // Account level bulk actions are not allowed on all item types, or within
        // a line item delegation since the user cant see all items.
        boolean allowAccountLevelActions = CertificationItem.allowAccountLevelActions(type,
                appName, certType);
        decisions.addStatus(CertificationAction.Status.Approved);

        // disable approve account if bulk account actions are not allowed,
        // or if the option was not enabled for the certification.
        if (!isLineItemDelegation && !CertificationItem.Type.Account.equals(type) &&
                definition.isAllowApproveAccounts(context) && allowAccountLevelActions) {
            // no enum for this action b/c it's technically not an actual
            // action. Rather it's a UI convenience so users do not need to
            // click approve for every item on an account.
            decisions.addStatus(CertificationAction.Status.ApproveAccount);
        }

        // Don't show the revoke button if this allows account-level actions and
        // the cert is at application granularity.
        boolean isAppGranularity = CertificationItem.Type.Account.equals(type) ||
                Certification.EntitlementGranularity.Application.equals(definition.getEntitlementGranularity());
        boolean noRevoke = isAppGranularity && allowAccountLevelActions;

        if (!noRevoke) {
            decisions.addStatus(CertificationAction.Status.Remediated);
        }

        // Revoke account is always allowed with app granularity account items, since there's
        // no other revoke decision to make
        // Do not show revoke account for line item delegations or bulk reassignments
        if (allowAccountLevelActions && (isAppGranularity || (!isLineItemDelegation && !isBulkReassignment &&
                allowAccountLevelActions && definition.isAllowAccountRevocation(context)))) {
            decisions.addStatus(CertificationAction.Status.RevokeAccount);
        }

        if (definition.isAllowExceptions(context)) {
            decisions.addStatus(CertificationAction.Status.Mitigated);
        }

        if (definition.isAllowItemDelegation(context) && !isLineItemDelegation) {
            decisions.addStatus(CertificationAction.Status.Delegated);
        }

        // If the item is an addtl entitlement or an account AND the item is not currently a delegation, allow
        // account reassignment.
        if (!isViewingDelegation && (CertificationItem.Type.Exception.equals(type) || CertificationItem.Type.Account.equals(type))
                && definition.isEnableReassignAccount(context)){
            decisions.addStatus(CertificationAction.Status.AccountReassign);
        }

        return decisions;
    }
}
