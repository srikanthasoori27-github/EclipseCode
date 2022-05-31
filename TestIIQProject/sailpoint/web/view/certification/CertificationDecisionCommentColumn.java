/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.view.certification;

import java.util.HashMap;
import java.util.Map;

import sailpoint.object.CertificationDelegation;
import sailpoint.object.CertificationItem;
import sailpoint.tools.GeneralException;

/**
 * @author: michael.hide
 * Created: 4/15/2016 09:17
 */
public class CertificationDecisionCommentColumn extends CertificationItemColumn {

    /**
     * Finds all the comments on a CertificationItem
     *
     * @param row
     * @return {Map} HashMap containing all available comments
     * @throws GeneralException
     */
    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException {
        Map output = new HashMap();

        CertificationItem item = getCertificationItem(row);

        // Get challenge comments
        if ((null != item.getChallenge()) && item.getChallenge().isChallenged()) {
            if (item.getChallenge().getCompletionComments() != null) {
                output.put("challengeCompletionComments", item.getChallenge().getCompletionComments());
            }
        }

        // Get delegation comments
        CertificationDelegation delegation = getDelegation(row);
        if (delegation != null) {
            output.put("delegationComments", delegation.getCompletionComments());
        }

        return output;
    }

    /**
     * Returns the item or entity delegation object if the current user has access.
     *
     * @param row
     * @return {CertificationDelegation} the delegation item
     * @throws GeneralException
     */
    private CertificationDelegation getDelegation(Map<String, Object> row) throws GeneralException {
        CertificationDelegation itemDel = getCertificationItem(row).getDelegation();
        CertificationDelegation identityDel = getCertificationEntity(row).getDelegation();

        CertificationDelegation delegation = itemDel;
        if (delegation == null) {
            delegation = identityDel;
        }

        if (delegation != null && isShowDelegationReview(row, delegation)) {
            // Null acting work item means that this was requested from the certification.
            if (null == delegation.getActingWorkItem()) {
                // Show if the logged in user is the certification owner, or
                // if we're viewing the certification report.
                if (getCertRole(row).isViewingCertification || getCertRole(row).isCertificationOwner) {
                    return delegation;
                }
                return null;
            }

            if (getCertRole(row).isItemDelegationRequester || getCertRole(row).isViewingIdentityWorkItem) {
                return itemDel;
            }

            if ((null != itemDel) &&
                    (null != identityDel) &&
                    !identityDel.isActive() &&
                    itemDel.getActingWorkItem().equals(identityDel.getWorkItem())) {
                return itemDel;
            }
        }

        return null;
    }

    /**
     * Tests if the current user should see this delegation.
     *
     * If the item/entity delegation is returned(rejected) we allow seeing the delegation completions comments.
     *
     * The CertificationItem::isReturned() method doesn't check the entity delegation which is why it gets passed in
     * here. The CertificationDelegation passed in is the item or entity delegation object.
     *
     * The CertificationItem::requiresReview() method does check the entity delegation which is why we can just call it.
     *
     * @param row
     * @param {CertificationDelegation} identity or item delegation
     * @return {Boolean} true if current user can see delegation review
     * @throws GeneralException
     */
    private boolean isShowDelegationReview(Map<String, Object> row, CertificationDelegation delegation) throws GeneralException {
        CertificationItem item = getCertificationItem(row);
        boolean identityDelActive = (null != item.getCertificationEntity().getDelegation()) ?
                item.getCertificationEntity().getDelegation().isActive() : false;
        // Only show in certification report to the owner.  If not
        // certification owner, we don't want to present the link to review.
        if (getCertRole(row).isViewingCertification && getCertRole(row).isCertificationOwner) {
            if (delegation.isReturned() || (!delegation.isReturned() && item.requiresReview() &&
                    (!getCertRole(row).wasItemDecidedDuringIdentityDelegation || !identityDelActive))) {
                return true;
            }
        }
        return false;
    }
}
