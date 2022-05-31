/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.view.certification;

import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sailpoint.api.CertificationService;
import sailpoint.api.IdentityHistoryService;
import sailpoint.api.SailPointContext;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationDelegation;
import sailpoint.object.CertificationItem;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityHistoryItem;
import sailpoint.object.QueryOptions;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.web.view.IdentitySummary;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class CertificationDecisionHistoryColumn extends CertificationItemColumn {



    private Date currentMitigation;
    private Date expiredMitigation;
    private boolean unremovedRemediation;
    private boolean unprovisionAddRequest;
    private Date lastMitigationDate;

    // Stores the additional width needed on the decision
    // column. This only comes into play if we have messages
    // about returned delegations, existing remediations etc
    // to show
    private int maxWidth = 0;

    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException {
        Map output = new HashMap();

        calculateHistory(row);

        CertificationItem item = getCertificationItem(row);

        String delegationId = null;
        if (null != item.getCertificationEntity() && item.getCertificationEntity().getDelegation() != null){
            delegationId = item.getCertificationEntity().getDelegation().getId();
        } else if (item.getDelegation() != null ){
            delegationId = item.getDelegation().getId();
        }

        if (delegationId != null)
            output.put("delegationId", delegationId);

        boolean showDelegationReview = isShowDelegationReview(row);
        if (showDelegationReview){
            output.put("showDelegationReview", true);
            setWidth(delegationId != null ? 305 : 225); // increase width to handle display
        }
        if (item.isDelegated())
            output.put("itemDelegated", true);

        if (null != item.getCertificationEntity() && item.getCertificationEntity().isEntityDelegated())
            output.put("identityDelegated", true);

        if (currentMitigation != null){
            output.put("currentMitigation", formatDate(currentMitigation));
            setWidth(100);
        }
        if (expiredMitigation != null){
            output.put("expiredMitigation", formatDate(expiredMitigation));
            setWidth(100);
        }
        if (unremovedRemediation)
            output.put("unremovedRemediation", true);
        if (unprovisionAddRequest)
            output.put("unprovisionedAddRequest",true);

        if (lastMitigationDate != null)
            output.put("lastMitigationDate", lastMitigationDate.getTime());

        CertificationService cSvc = new CertificationService(getSailPointContext());
        Identity identity = this.getIdentity(row);
        if (identity != null){
            boolean hasComment = cSvc.hasComment(identity.getId(), item.getId());
            output.put("hasComments", hasComment);
        }

        if (item.getAction() != null ){
            output.put("actionId", item.getAction().getId());
            if (item.getAction().getOwnerName() != null){
                Identity owner = getSailPointContext().getObjectByName(Identity.class, item.getAction().getOwnerName());
                if (owner != null)
                    output.put("actionOwner", new IdentitySummary(owner));
            }

            if (item.getAction().getWorkItem() != null){
                output.put("actionWorkItem", item.getAction().getWorkItem());
                if (item.getAction().getCompletionComments() != null
                        && item.getAction().getCompletionComments().length()>0)
                    output.put("remediationComments", item.getAction().getCompletionComments());
            }
        }

        if ((null != item.getChallenge()) && item.getChallenge().isChallenged()) {
            output.put("showChallengeExpiration", item.getChallenge().isChallengeDecisionExpired());
            output.put("showChallenge", !item.getChallenge().isChallengeDecisionExpired());
            output.put("allowChallengeDecision", !item.getChallenge().isChallengeDecisionExpired() &&
                    !item.getChallenge().hasBeenDecidedUpon());

            if (item.getChallenge().getDecision() != null){
                output.put("challengeDecision", item.getChallenge().getDecision().name());
                output.put("challengeDecisionName",
                        Internationalizer.getMessage(item.getChallenge().getDecision().getMessageKey(), getLocale()));
            }

            if (item.getChallenge().getOwnerName() != null){
                Identity challengeOwner = this.getSailPointContext().getObjectByName(Identity.class,
                        item.getChallenge().getOwnerName());
                if (challengeOwner != null)
                    output.put("challengeOwner", new IdentitySummary(challengeOwner));
            }

            if (item.getChallenge().getCompletionComments() != null){
                output.put("challengeCompletionComments", item.getChallenge().getCompletionComments());
            }

            if (item.getChallenge().getDecisionComments() != null){
                output.put("challengeDecisionComments", item.getChallenge().getDecisionComments());
            }
        }

        CertificationAction.Status status = item.getAction() != null ? item.getAction().getStatus() : null;
        if (status != null && CertificationAction.Status.Remediated.equals(status)
                && !item.getAction().isActive()) {
            output.put("showRemediationComments", true);
            setWidth(120);
        }

        CertificationDelegation finishedDelegation = calculateShowDelegationComments(row, WorkItem.State.Finished);
        if (finishedDelegation != null && finishedDelegation.getCompletionComments() != null){
            output.put("showDelegationComments", true);
            Identity owner = getSailPointContext().getObjectByName(Identity.class, finishedDelegation.getOwnerName());
            if (owner != null){
                output.put("delegationOwner", owner.getDisplayableName());
            }
        }

        CertificationDelegation returnedDelegation = calculateShowDelegationComments(row, WorkItem.State.Returned);
        if (returnedDelegation != null){
            output.put("showReturnedDelegation", true);
            setWidth(delegationId != null ? 305 : 225); // increase width to handle display
            Identity owner = getSailPointContext().getObjectByName(Identity.class, returnedDelegation.getOwnerName());
            if (owner != null){
                output.put("returnedDelegationOwner", owner.getDisplayableName());
            }
        }


        return output;
    }

    @Override
    public void afterRender() {
        List<ColumnConfig> columns = getContext().getColumns();
        if (columns != null && maxWidth > 0){
            for(ColumnConfig theColumn : columns){
                if (CertificationItemDecisionColumn.class.getName().equals(theColumn.getEvaluator())){
                    if (theColumn.getWidth() < maxWidth)
                        theColumn.setWidth(maxWidth);
                }
            }
        }
    }

    //---------------------------------------------------------------
    //
    // Private Methods
    //
    //---------------------------------------------------------------

    private String formatDate(Date dt){
        if (dt != null)
            return Internationalizer.getLocalizedDate(dt, DateFormat.SHORT, null, getLocale(), getTimeZone());

        return null;
    }

    /**
     * Set the width value if it's greater than the existing max width
     */
    private void setWidth(int w){
        if (w > maxWidth)
            maxWidth=w;
    }

    /**
     * Calculate whether the delegation comments for a completed item
     * delegation should be displayed for a delegation with the given
     * completion state.
     */
    private CertificationDelegation calculateShowDelegationComments(Map<String, Object> row, WorkItem.State state)
            throws GeneralException{

        // Show the delegation comments if:
        //  - There is a completed/returned, non-review required item-level
        //    delegation.
        //  AND
        //  - The item was delegated from the certification, OR
        //  - The logged in user requested the delegation, OR
        //  - We're viewing the identity work item in which the item
        //    delegation was requested, OR
        //  - The identity delegation in which the item was delegated is
        //    no longer active.
        CertificationDelegation itemDel = getCertificationItem(row).getDelegation();
        CertificationDelegation identityDel = getCertificationEntity(row).getDelegation();

        CertificationDelegation delegation = itemDel;
        if (delegation == null){
            delegation = identityDel;
        }

        if ((null != delegation) && !isShowDelegationReview(row) &&
            state.equals(delegation.getCompletionState())) {

            // Null acting work item means that this was requested from the
            // certification.
            if (null == delegation.getActingWorkItem()) {
                // Show if the logged in user is the certification owner, or
                // if we're viewing the certification report.
                if (getCertRole(row).isViewingCertification || getCertRole(row).isCertificationOwner)
                    return delegation;
                return null;
            }

            if (getCertRole(row).isItemDelegationRequester || getCertRole(row).isViewingIdentityWorkItem) {
                return itemDel;
            }

            if ((null != itemDel) && (null != identityDel) &&
                !identityDel.isActive() &&
                itemDel.getActingWorkItem().equals(identityDel.getWorkItem())) {
                return itemDel;
            }
        }
        return null;
    }

    private void calculateHistory(Map<String, Object> row) throws GeneralException{

        this.currentMitigation = null;
        this.lastMitigationDate = null;
        this.expiredMitigation = null;
        this.unremovedRemediation = false;
        this.unprovisionAddRequest = false;

        CertificationItem item = getCertificationItem(row);
        IdentityHistoryItem lastDecision = getLastDecision(item);
        if (null != lastDecision) {
            CertificationAction action = lastDecision.getAction();
            if (action != null) { // dunno how, but bug 14678 says it could happen
                if (CertificationAction.Status.Mitigated.equals(action.getStatus())) {
                    this.lastMitigationDate = action.getMitigationExpiration();

                    // If now < lastMitigationDate (last mitigation date hasn't passed).
                    if ((new Date().compareTo(this.lastMitigationDate)) < 0) {
                        this.currentMitigation = lastMitigationDate;
                    }
                    else {
                        this.expiredMitigation = lastMitigationDate;
                    }
                }
                else if (CertificationAction.Status.Remediated.equals(action.getStatus())) {
                    boolean isRemediationCompleted = action.isRemediationCompleted();

                    // the action may have changed since it was stored on in the identity
                    // history. Check to see if we can get the live action object to see
                    // if the remediation is complete
                    if (!isRemediationCompleted){
                        Iterator<Object[]> searchResults = getSailPointContext().search(CertificationAction.class,
                                new QueryOptions(Filter.eq("id", action.getId())), Arrays.asList("remediationCompleted"));
                        if (searchResults != null && searchResults.hasNext()){
                            isRemediationCompleted = (Boolean)searchResults.next()[0];
                        }
                    }

                    this.unremovedRemediation = !isRemediationCompleted;
                }
                else if (CertificationAction.Status.Approved.equals(action.getStatus()) &&
                        action.getRemediationDetails() != null) {
                    this.unprovisionAddRequest = true;
                }
            }
        }
    }

    private boolean isShowDelegationReview(Map<String, Object> row) throws GeneralException{
        CertificationItem item = getCertificationItem(row);
        boolean identityDelActive =
            (null != item.getCertificationEntity().getDelegation()) ?
                    item.getCertificationEntity().getDelegation().isActive() : false;
        // Only show in certification report to the owner.  If not
        // certification owner, we don't want to present the link to review.
        if (getCertRole(row).isViewingCertification && getCertRole(row).isCertificationOwner) {
            if (!item.isReturned() &&
                item.requiresReview() &&
                (!getCertRole(row).wasItemDecidedDuringIdentityDelegation ||
                 !identityDelActive)) {
                return true;
            }
        }
        return false;
    }

    private IdentityHistoryItem getLastDecision(CertificationItem item) throws GeneralException{
        SailPointContext context = getContext().getSailPointContext();
        Identity identity = context.getObjectByName(Identity.class, item.getIdentity());
        if (identity != null) {
            IdentityHistoryService svc = new IdentityHistoryService(context);
            return svc.getLastDecision(identity.getId(), item);
        } else {
            return null;
        }
    }
}
