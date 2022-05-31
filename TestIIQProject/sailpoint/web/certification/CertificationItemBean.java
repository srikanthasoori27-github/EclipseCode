/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 *
 * NOTE: This class is included in the pubic javadocs.
 *
 */

package sailpoint.web.certification;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.faces.el.ValueBinding;
import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CertificationService;
import sailpoint.api.Explanator;
import sailpoint.api.Iconifier;
import sailpoint.api.Iconifier.Icon;
import sailpoint.api.IdentityHistoryService;
import sailpoint.api.Meter;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.api.certification.RemediationManager;
import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.connector.Connector;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Certification;
import sailpoint.object.Certification.Phase;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationAction.Status;
import sailpoint.object.CertificationChallenge;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationDelegation;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.Configuration;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Entitlements;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityDifference;
import sailpoint.object.IdentityHistoryItem;
import sailpoint.object.IdentitySnapshot;
import sailpoint.object.Link;
import sailpoint.object.LinkSnapshot;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleSnapshot;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.SPRight;
import sailpoint.object.SailPointObject;
import sailpoint.object.Schema;
import sailpoint.object.Snapshot;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkItemMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.LoadingMap;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.AccountIdBean;
import sailpoint.web.BaseBean;
import sailpoint.web.identity.IdentityDTO;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.policy.ViolationViewBean;
import sailpoint.web.util.WebUtil;

/**
 * UI bean classes for items within certifications.
 */
public class CertificationItemBean extends BaseBean {

    private static final Log LOG = LogFactory.getLog(CertificationItemBean.class);

    ////////////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    ////////////////////////////////////////////////////////////////////////////

    protected BaseBean baseBean;
    protected CertificationEntity entity;
    protected CertificationItem item;
    protected String workItem;

    private CertificationDefinition certDefinition;
    private String id;
    private String certificationIdentityId;

    protected String certificationIdentityName;

    /**
     * The editable status of this item. This status might not necessarily
     * reflect the item decision - depending on whether the item/entity is
     * delegated and how this item is being viewed.
     */
    private CertificationAction.Status status;

    /**
     * The status of this item as pulled from the database.
     */
    private CertificationAction.Status previousStatus;

    private boolean delegationPendingReview;
    private boolean delegationReturned;
    private boolean itemDelegated;
    private boolean identityDelegated;

    private boolean readOnly;
    private boolean canChangeDecision;
    private boolean showDelegationReview;
    private boolean showDelegationComments;
    private boolean showReturnedDelegation;
    private boolean showRemediationComments;
    private boolean showChallenge;
    private boolean showChallengeExpiration;
    private boolean allowChallengeDecision;

    private boolean showRemediationDialog;

    //
    // Fields about the last certification decision.
    //

    /**
     * True if the last decision was a remediation, but the item still exists.
     */
    private boolean unremovedRemediation;

    /**
     * True if the a 'Add' provisioning operation was requested but not
     * yet completed.
    */
    private boolean provisionAddsRequest;

    /**
     * True if the last certification decision was a mitigation that has expired.
     */
    private boolean expiredMitigation;

    /** True if there is a comment associated with this certification item **/
    private Boolean showComment;

    private boolean lastDecisionInspected;
    
    /**
     * True if the last certification decision was a mitigation that is still
     * current (for example - it has not expired).
     */
    private boolean currentMitigation;

    /**
     * The mitigation date of the last certification decision (if it was
     * mitigated).
     */
    private Date lastMitigationDate;


    protected List<SelectItem> statusChoices;

    private CertificationDelegation delegation;
    private CertificationAction action;
    private CertificationChallenge challenge;

    private CertificationEntity.Type entityType;

    private Boolean enableApproveAccount;
    private Boolean enableRevokeAccount;


    ////////////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor used by JSF.
     */
    public CertificationItemBean() {}

    /**
     * Constructor used in code.
     *
     * @param  bean          The BaseBean creating this.
     * @param  item          The certification item for this entitlement.
     * @param  entity        The certification entity for this entitlement.
     * @param  workItem      The ID of the work item in which this entitlement
     *                       is being viewed.  If null, the entitlement is
     *                       being viewed from the within the certification.
     */
    public CertificationItemBean(BaseBean bean, CertificationItem item,
                                 CertificationEntity entity, String workItem)
        throws GeneralException
    {
        Meter.enterByName("CertificationItemBean constructor");

        // don't authorized b/c we can assume this bean is being instantiated
        // by an object which is already authorized
        this.setSkipAuthorization(true);

        this.baseBean = bean;
        this.item = item;
        this.entity = entity;
        this.workItem = workItem;

        this.id = item.getId();
        loadCertificationIdentityInfo(entity, item);
        this.entityType = (null != entity) ? entity.getType() : null;

        this.certDefinition = item.getCertification().getCertificationDefinition(getContext());

        if (certDefinition == null) {
            certDefinition = new CertificationDefinition();
            certDefinition.initialize(getContext());
        }

        postInit();

        Meter.exitByName("CertificationItemBean constructor");
    }

    /**
     * Not all CertificationEntities are of 'Identity' type so we need to dig
     * deeper to find the identity information.
     * For instance, in case of dataowner certifications the certification item will have
     * the identity details
     */
    private void loadCertificationIdentityInfo(CertificationEntity entity, CertificationItem item) {
        
        switch (entity.getType()) {
        case DataOwner:
            // In dataowner type certs
            this.certificationIdentityId = item.getTargetId();
            this.certificationIdentityName = item.getTargetName();
            break;
        default:
            this.certificationIdentityId = (null != entity) ? entity.getId() : null;
            this.certificationIdentityName = (null != entity) ? entity.getIdentity() : null;
            break;
        }
        
    }
    
    private void postInit() throws GeneralException {

        this.itemDelegated = item.isDelegated();
        this.identityDelegated = null != entity && entity.isEntityDelegated();

        Meter.enterByName("CertificationItem : Calculating delegation");
        this.delegationPendingReview = item.requiresReview();
        this.delegationReturned = item.isReturned();
        Meter.exitByName("CertificationItem : Calculating delegation");

        // TODO: Calculating UI menu stuff is slow.  Should we load on demand?
        Meter.enterByName("CertificationItem : Calculating UI menu");
        Role role = new Role(this.baseBean, item, entity, workItem);

        this.readOnly = calculateReadOnly(item, entity, role);
        this.canChangeDecision = calculateCanChangeDecision(item);

        CertificationAction.Status status = null;
        if (null != this.getAction()) {
            status = this.getAction().getStatus();

            // Use the revoke account pseudo-status for account revokes.
            if (CertificationAction.Status.Remediated.equals(status) &&
                getAction().isRevokeAccount()) {
                status = CertificationAction.Status.RevokeAccount;
            }

            // Acknowledgments are treated as mitigations in the UI
            if (CertificationAction.Status.Acknowledged.equals(status)){
                status = CertificationAction.Status.Mitigated;
            }
        }
        this.status = calculateStatus(role, status);

        // Save the previous status after the status has been calculated.  This
        // is used in this bean to determine if a decision has been changed for
        // an item, and used in the UI to determine whether clicking a radio
        // should cause a change or revoke a delegation.
        this.previousStatus = this.status;

        this.showDelegationReview = calculateShowDelegationReview(role, item, entity);
        this.showReturnedDelegation = calculateShowReturnedDelegation(role, item, entity);
        this.showDelegationComments = calculateShowDelegationComments(role, item, entity);
        this.showRemediationComments = calculateShowRemediationComments();
        Meter.exitByName("CertificationItem : Calculating UI menu");

        Meter.enterByName("CertificationItem : Calculating challenge");
        CertificationChallenge challenge = item.getChallenge();
        if ((null != challenge) && challenge.isChallenged()) {
            if (challenge.isChallengeDecisionExpired()) {
                this.showChallengeExpiration = true;
            }
            else {
                this.showChallenge = true;
            }

            this.allowChallengeDecision =
                this.showChallenge && !challenge.hasBeenDecidedUpon();
        }
        Meter.exitByName("CertificationItem : Calculating challenge");

        Meter.enterByName("CertificationItem : Calculating show remediate dialog");
        this.showRemediationDialog = calculateShowRemediationDialog(item, entity, workItem);
        Meter.exitByName("CertificationItem : Calculating show remediate dialog");
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Methods
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    protected boolean isAuthorized(SailPointObject object) throws GeneralException {

        if (getLoggedInUserRights() != null && getLoggedInUserRights().contains(SPRight.FullAccessCertifications))
            return true;

        Certification cert = (Certification) object;
        if (CertificationAuthorizer.isReassignmentParentOwner(getLoggedInUser(), cert)) {
            return true;
        }

        return super.isAuthorized(object);
    }

    /**
     * Save this item if it has changed. This throws an exception if this is
     * not an Approve or Remediate, since the other decisions are saved through
     * popups.
     *
     * @param  workItemId  The ID of the work item doing the saving.
     */
    public void saveIfChanged(String workItemId) throws GeneralException {

        if (hasStatusChanged()) {

            CertificationItem certItem =
                baseBean.getContext().getObjectById(CertificationItem.class, this.id);

            if (CertificationAction.Status.Approved.equals(this.status)) {
                certItem.approve(getContext(), baseBean.getLoggedInUser(), workItemId, null);
            }
            else if (CertificationAction.Status.Remediated.equals(this.status)) {
                // Using the certification action gets us the default description,
                // remediator, etc...
                CertificationActionBean action =
                    new CertificationActionBean(certItem, workItemId,
                                                CertificationAction.Status.Remediated);
                action.saveRemediation();
            }
            else if (CertificationAction.Status.RevokeAccount.equals(this.status)) {
                // Using the certification action gets us the default description,
                // remediator, etc...
                CertificationActionBean action =
                    new CertificationActionBean(certItem, workItemId,
                                                CertificationAction.Status.RevokeAccount);
                action.saveRevokeAccount();
            }
            else {
                throw new GeneralException("Should have already saved certification item " + this.id);
            }
        }
    }


    /**
     * Return whether the status has changed (for example - a new decision has been
     * selected) for this item.
     */
    boolean hasStatusChanged() {

        return !Util.nullSafeEq(this.status, this.previousStatus, true);
    }

    public boolean isShowComment() throws GeneralException {
        if (null == this.showComment) {
            this.showComment = calculateHasComment();
        }

        return this.showComment;
    }

    private boolean calculateHasComment() throws GeneralException {
        CertificationService cSvc = new CertificationService(getContext());

        String identityId = entity.getTargetId();

        // for account group membership certs, the identity id if the item target
        if (CertificationEntity.Type.AccountGroup.equals(entity.getType())){
            identityId = item.getTargetId();
        }

        return cSvc.hasComment(identityId, getId());
    }

    /**
     * Return the text that is displayed when the given status has been chosen
     * for an item.
     *
     * @param  status  The status for which to return the text.
     *
     * @return The text that is displayed when the given status has been chosen
     *         for an item.
     */
    String getStatusText(CertificationAction.Status status) {
        return this.baseBean.getMessage(status.getMessageKey());
    }

    /**
     * Calculate whether this item's decision is read-only or not.
     * This depends on the logged in user's relation to this item in
     * the certification and whether the certification has been signed or
     * not.
     */
    private boolean calculateReadOnly(CertificationItem item,
                                      CertificationEntity identity,
                                      Role role)
        throws GeneralException {

        // First, if the certification has been signed, nothing can be
        // changed.
        Certification cert = identity.getCertification();
        if (cert!=null && cert.hasBeenSigned()) {
            return true;
        }

        // Also, check if the decision can be changed.
        if (!this.calculateCanChangeDecision(item)) {
            return true;
        }

        CertificationAction action = item.getAction();

        // Case 0 - Neither identity or item is delegated, not read-only if
        // certifier is logged in.
        if (!this.itemDelegated && !this.identityDelegated)
            return !role.isCertificationOwner;

        // Case 1 - Item is delegated, identity is not.
        //  Editable if delegation owner is the logged in user or if the
        //  person that delegated the item is the logged in user (this
        //  allows the person that delegated the item to revoke it).
        if (this.itemDelegated && !this.identityDelegated) {
            if (role.isItemDelegationOwner ||
                (role.isItemDelegationRequester && !role.isViewingItemWorkItem) ||
                (role.isCertificationOwner && role.isCertifierItemDelegationRequester &&
                 role.isViewingCertification))
                return false;
            else return true;
        }

        // Case 2 - Identity is delegated, item is not.
        //  If delegation owner is logged in, item is editable if the action
        //  has not been set OR if the logged in user made the action decision.
        //
        //  If delegation owner is not logged in, item is only editable if
        //  the action was originally decided by the logged in user, or if
        //  the certification owner is looking at a returned item in
        //  the certification report.
        if (this.identityDelegated && !this.itemDelegated) {

            boolean isReturnedItemRequester =
                this.delegationReturned && role.isItemDelegationRequester;

            if (role.isIdentityDelegationOwner) {
                if ((isReturnedItemRequester || !this.delegationReturned) &&
                    ((null == action) || role.isItemActionActor))
                    return false;
                else if (!role.wasItemDecidedOutsideOfIdentityDelegation)
                    return false;
                else if (role.isCertificationOwner && role.isViewingCertification &&
                         role.wasItemDecidedOutsideOfIdentityDelegation)
                    return false;
                else return true;
            }
            else {
                if (role.isItemActionActor) {
                    return false;
                }
                else if (isReturnedItemRequester && role.isViewingCertification) {
                    return false;
                }
                else if (role.isCertificationOwner && role.isViewingCertification &&
                        role.wasItemDecidedOutsideOfIdentityDelegation)
                   return false;
                else return true;
            }
        }

        // Case 3 - Identity and item are delegated.
        //  If the identity delegation owner is the logged in user, the
        //  item delegation is only editable if the logged in user delegated
        //  the item.
        //
        //  If the item delegation owner is the logged in user, the item is
        //  editable.
        //
        //  If the person that delegated the item is the logged in user, the
        //  delegation should be editable.
        if (this.identityDelegated && this.itemDelegated) {
            if (role.isIdentityDelegationOwner) {
                if (role.isItemDelegationRequester)
                    return false;
                else return true;
            }
            else if (role.isItemDelegationOwner) {
                return false;
            }
            else if (role.isItemDelegationRequester) {
                return false;
            }
            else return true;
        }

        LOG.warn("Should have already decided if item is read-only, " +
                 "defaulting to read-only. " + item);
        return true;
    }

    /**
     * Calculate whether an existing decision can be changed.
     */
    private boolean calculateCanChangeDecision(CertificationItem item) {

        Certification cert = item.getCertification();
        Phase phase = item.getPhase();
        CertificationAction action = item.getAction();

        return !CertificationItem.isDecisionLockedByPhase(cert, action, phase) &&
               !CertificationItem.isDecisionLockedByRevokes(cert, item.getDelegation(),
                       item.getParent().getDelegation(), action);
    }

    /**
     * Calculate the status to display for the item. This depends on the
     * delegation status of the item/entity and the role (who is viewing which
     * page).
     *
     * @param  role          The role of the currently logged in user.
     * @param  actionStatus  The status of the action for the item.
     */
    private CertificationAction.Status calculateStatus(Role role,
                                                       CertificationAction.Status actionStatus) {

        CertificationAction.Status status = actionStatus;

        // In certification, the following:
        //  - Item is delegated -> Show Delegated.
        //  - Identity is delegated
        //     If this user made the decision show the status, otherwise
        //     show as delegated.
        if (role.isViewingCertification) {
            if (this.itemDelegated) {
                status = CertificationAction.Status.Delegated;
            }
            else if (this.identityDelegated) {
                if (role.isItemActionActor ||
                    role.wasItemDecidedOutsideOfIdentityDelegation) {
                    status = actionStatus;
                }
                else {
                    status = CertificationAction.Status.Delegated;
                }
            }
        }
        else {
            // In work item, the following:
            //  - Item is delegated
            //     If this user made the decision or if we're viewing the work
            //     item for this certification item, show the action's status.
            //
            //     If this user delegated the item, or delegated the identity,
            //     or owns the identity delegation, show Delegated.
            //
            //  - Identity is delegated
            //     Show the action's status or "No Decision".
            if (this.itemDelegated) {
                if (role.isItemActionActor || role.isViewingItemWorkItem) {
                    status = actionStatus;
                }
                else if (role.isItemDelegationRequester ||
                         role.isIdentityDelegationRequester ||
                         role.isIdentityDelegationOwner) {
                    status = CertificationAction.Status.Delegated;
                }
            }
            else if (this.identityDelegated) {
                status = actionStatus;
            }
        }

        return status;
    }

    /**
     * Calculate whether the delegation comments for a completed item
     * delegation should be displayed.
     *
     * @param  role  The Role of the logged in user.
     * @return true if the delegation comments for a completed item delegation should be displayed. False otherwise.
     */
    private boolean calculateShowDelegationComments(Role role,
                                                    CertificationItem item,
                                                    CertificationEntity identity) {
        return calculateShowDelegationComments(WorkItem.State.Finished,
                                               role, item, identity);
    }

    /**
     * Calculate whether to show that the delegation for this item was
     * returned.
     *
     * @param  role  The Role of the logged in user.
     */
    private boolean calculateShowReturnedDelegation(Role role,
                                                    CertificationItem item,
                                                    CertificationEntity identity) {
        return calculateShowDelegationComments(WorkItem.State.Returned,
                                               role, item, identity);
    }

    /**
     * Calculate whether the delegation comments for a completed item
     * delegation should be displayed for a delegation with the given
     * completion state.
     *
     * @param  role  The Role of the logged in user.
     */
    private boolean calculateShowDelegationComments(WorkItem.State state,
                                                    Role role,
                                                    CertificationItem item,
                                                    CertificationEntity identity) {

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
        if ((null != this.getDelegation()) && !this.showDelegationReview &&
            state.equals(this.getDelegation().getCompletionState())) {

            // Null acting work item means that this was requested from the
            // certification.
            if (null == this.getDelegation().getActingWorkItem()) {
                // Show if the logged in user is the certification owner, or
                // if we're viewing the certification report.
                if (role.isViewingCertification || role.isCertificationOwner)
                    return true;
                return false;
            }
            if (role.isItemDelegationRequester) {
                return true;
            }
            if (role.isViewingIdentityWorkItem) {
                return true;
            }

            CertificationDelegation itemDel = item.getDelegation();
            CertificationDelegation identityDel = identity.getDelegation();
            if ((null != itemDel) && (null != identityDel) &&
                !identityDel.isActive() &&
                itemDel.getActingWorkItem().equals(identityDel.getWorkItem())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calculate whether remediation comments should be displayed or not.
     */
    private boolean calculateShowRemediationComments() {

        if ((null != getAction()) &&
            CertificationAction.Status.Remediated.equals(getAction().getStatus()) &&
            !getAction().isActive()) {
            return true;
        }
        return false;
    }

    /**
     * Calculate whether to display the delegation review link.
     */
    private boolean calculateShowDelegationReview(Role role, CertificationItem item,
                                                  CertificationEntity identity) {

        boolean identityDelActive =
            (null != identity.getDelegation()) ? identity.getDelegation().isActive() : false;
        // Only show in certification report to the owner.  If not
        // certification owner, we don't want to present the link to review.
        if (role.isViewingCertification && role.isCertificationOwner) {
            if (!this.delegationReturned &&
                this.delegationPendingReview &&
                (!role.wasItemDecidedDuringIdentityDelegation ||
                 !identityDelActive)) {
                return true;
            }
        }

        return false;
    }


    /**
     * Calculate the list of item decision choices.
     */
    List<SelectItem> calculateStatusChoices(Role role) throws GeneralException {

        List<SelectItem> list = new ArrayList<SelectItem>();

        list.add(new SelectItem(CertificationAction.Status.Approved, "approveRadio"));

        boolean enableAccountLevelActions = this.item.allowAccountLevelActions();
        if (!CertificationItem.Type.Account.equals(this.item.getType()) &&
                isApproveAccountAllowed(role) && enableAccountLevelActions) {
            // no enum for this action b/c it's technically not an actual
            // action. Rather it's a UI convenience so users do not need to
            // click approve for every item on an account.
            list.add(new SelectItem(CertificationAction.Status.ApproveAccount, "approveAccountRadio"));
        }

        // Don't show the revoke button if this allows account-level actions and
        // the cert is at application granularity.
        boolean noRevoke = this.item.useRevokeAccountInsteadOfRevoke();
        if (!noRevoke) {
            list.add(new SelectItem(CertificationAction.Status.Remediated, "revokeRadio"));
        }

        // Note that revoke account is shown even if isRevokeAccountAllowed()
        // returns false if we're not going to allow revoke item.
        if ( enableAccountLevelActions && isRevokeAccountAllowed(role)) {
            list.add(new SelectItem(CertificationAction.Status.RevokeAccount, "revokeAccountRadio"));
        }

        boolean allowExceptions = certDefinition.isAllowExceptions(getContext());;

        // busines role certs items and account group certs items cannot be mitigated
        if (!CertificationEntity.Type.AccountGroup.equals(entityType) &&
            !CertificationEntity.Type.BusinessRole.equals(entityType) &&
            allowExceptions ) {
            list.add(new SelectItem(CertificationAction.Status.Mitigated, "mitigateRadio"));
        }

        boolean delegationEnabled = certDefinition.isAllowItemDelegation(getContext());

        // Don't allow delegating from within an item-delegation work item.
        if (!role.isViewingItemWorkItem  && delegationEnabled) {
            list.add(new SelectItem(CertificationAction.Status.Delegated, "delegateRadio"));
        }

        return list;
    }

    /**
     * 
     * Note that this is only called if the item is one of the types that
     * supports "revoke account".
     * @return true if the "revoke account" decision is allowed for this item.
     * @throws GeneralException
     */
    boolean isRevokeAccountAllowed(Role role) throws GeneralException {
        if (role.isViewingItemWorkItem)
            return false;

        if (enableRevokeAccount == null){
            enableRevokeAccount = certDefinition.isAllowAccountRevocation(getContext());
        }
        return enableRevokeAccount;
    }

    /**
     * @return True if certification allows approve account action.
     */
    boolean isApproveAccountAllowed(Role role) throws GeneralException{

       if (role.isViewingItemWorkItem)
           return false;

       if (enableApproveAccount == null){
           enableApproveAccount = certDefinition.isAllowApproveAccounts(getContext());
       }
       return enableApproveAccount;
    }


    /**
     * Calculate whether we need to prompt for more remediation details for this
     * item.
     *
     * @param  item  The CertificationItem.
     *
     * @return True if a remediation dialog is required, false otherwise.
     */
    boolean calculateShowRemediationDialog(CertificationItem item,
                                           CertificationEntity entity,
                                           String workItemId)
        throws GeneralException {

        // We are now always returning true here to speed up the calculation of
        // the page.  This information is later retrieved by AJAX via the
        // CertificationItemSecondPassBean.
        return true;
    }

    /**
     * @return true if auto-remediation is enabled for the the given item.
     */
    boolean isAutoRemediationEnabled(Certification cert, CertificationItem item)
        throws GeneralException {

        RemediationManager mgr = new RemediationManager(baseBean.getContext());
        RemediationManager.ProvisioningPlanSummary plan = mgr.calculateRemediationDetails(item, null);
        return CertificationAction.RemediationAction.SendProvisionRequest.equals(plan.getAction());
    }

    public String refresh() throws GeneralException{

        this.inspectLastDecision();
        return null;
    }

    /**
     * Look at the last certification decision and figure out whether there is
     * an expired mitigation, current mitigation, etc..
     */
    private void inspectLastDecision()
        throws GeneralException {

        // Only do this once.
        if (this.lastDecisionInspected) {
            return;
        }
        
        Identity identity = getContext().getObjectByName(Identity.class, item.getIdentity());
        if (identity == null) {
            LOG.warn("No identity found: " + item.getIdentity());
            return;
        }
        
        IdentityHistoryService svc = new IdentityHistoryService(getContext());
        IdentityHistoryItem lastDecision = svc.getLastDecision(identity.getId(), this.item);
        
        if (null != lastDecision) {
            CertificationAction action = lastDecision.getAction();
            if (CertificationAction.Status.Mitigated.equals(action.getStatus())) {
                this.lastMitigationDate = action.getMitigationExpiration();

                // If now < lastMitigationDate (last mitigation date hasn't passed).
                if ((new Date().compareTo(this.lastMitigationDate)) < 0) {
                    this.currentMitigation = true;
                }
                else {
                    this.expiredMitigation = true;
                }
            }
            else if (CertificationAction.Status.Remediated.equals(action.getStatus())) {
                this.unremovedRemediation = true;
            }
            else if (CertificationAction.Status.Approved.equals(action.getStatus()) &&
                    action.getRemediationDetails() != null) {
                this.provisionAddsRequest = true;
            }
        }
        this.lastDecisionInspected = true;
    }


    /**
     * Indicates whether or not we should display certain UI elements.
     * Currently that is whether or not we show history and the add comment
     * on the menu.
     *
     * @return True if this is an account group certification
     */
    public boolean isAccountGroup(){
        return CertificationEntity.Type.AccountGroup.equals(entityType);
    }

    public boolean isAccountGroupMembership(){
        return CertificationItem.Type.AccountGroupMembership.equals(item.getType());
    }

    public String getAccountGroupDescription() throws GeneralException{

        String desc = null;
        if (isAccountGroup() && item.getEntitlements() != null && !item.getEntitlements().isEmpty()){
            Application app = item.getEntitlements().get(0).getApplicationObject(getContext());

            // jsl - this is expensive, don't we have enough in the item
            // to call Explanator without fetching the MA?
            // item.getIdentity should be the same as group.getValue are we
            // only missing the group attribute name?
            ManagedAttribute group = item.getParent().getAccountGroup(getContext());

            desc = Explanator.getDescription(app, group.getAttribute(), group.getValue(), getLocale());
        }

        return desc;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Getters and Setters
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getId()
    {
        return this.id;
    }
    public String getCertificationIdentityId() {
        return this.certificationIdentityId;
    }
    public CertificationAction.Status getStatus()
    {
        return status;
    }
    public void setStatus(CertificationAction.Status status)
    {
        this.status = status;
    }

    public CertificationItem getItem() {
        return this.item;
    }

    public String getIdentityId() throws GeneralException {
        String id = null;

        Identity identity = this.entity.getIdentity(getContext());
        if (null != identity) {
            id = identity.getId();
        }

        return id;
    }

    // The custom attribute getters/setters delegate to the underlying
    // CertificationItem, so changes are saved immediately.  This is different
    // than changes to the action, which get must get saved explicitly.
    public String getCustom1() {
        return this.item.getCustom1();
    }

    public void setCustom1(String s) {
        this.item.setCustom1(s);
    }

    public String getCustom2() {
        return this.item.getCustom2();
    }

    public void setCustom2(String s) {
        this.item.setCustom2(s);
    }

    public Map<String,Object> getCustomMap() {
        return this.item.getCustomMap();
    }

    public void setCustomMap(Map<String,Object> m) {
        this.item.setCustomMap(m);
    }

    public CertificationAction getAction() {
        if ((null == this.action) && (null != item)) {
            this.action = item.getAction();
        }
        return action;
    }

    public CertificationDelegation getDelegation() {
        if ((null == this.delegation) && (null != item)) {
            this.delegation = item.getDelegation();
        }
        return delegation;
    }

    public CertificationChallenge getChallenge() {
        if ((null == this.challenge) && (null != item)) {
            this.challenge = item.getChallenge();
        }
        return this.challenge;
    }

    public boolean isItemDelegated()
    {
        return itemDelegated;
    }
    public boolean isIdentityDelegated()
    {
        return identityDelegated;
    }
    public boolean isDelegationPendingReview()
    {
        return delegationPendingReview;
    }
    public boolean isDelegationReturned()
    {
        return delegationReturned;
    }
    public boolean isReadOnly()
    {
        return this.readOnly;
    }
    public boolean getCanChangeDecision()
    {
        return this.canChangeDecision;
    }
    public boolean isShowDelegationReview()
    {
        return this.showDelegationReview;
    }
    public boolean isShowDelegationComments()
    {
        return this.showDelegationComments;
    }
    public boolean isShowReturnedDelegation()
    {
        return this.showReturnedDelegation;
    }
    public boolean isShowRemediationComments()
    {
        return this.showRemediationComments;
    }
    public boolean isShowChallenge() {
        return this.showChallenge;
    }
    public boolean isAllowChallengeDecision() {
        return this.allowChallengeDecision;
    }
    public boolean isShowChallengeExpiration() {
        return this.showChallengeExpiration;
    }
    public boolean isShowRemediationDialog() {
        return this.showRemediationDialog;
    }
    

    public boolean isUnremovedRemediation() throws GeneralException {
        // Calling inspectLastDecision() ensures that this is initialized.
        inspectLastDecision();
        return unremovedRemediation;
    }


    public boolean isUnprovisionedAddRequest() throws GeneralException {

        // short-circuit so we don't initialize this unless needed
        if (!isMissingRequirements())
            return false;

        inspectLastDecision();
        return provisionAddsRequest;
    }

    /**
     * Only implemented by BusinessRoleBean for now.
     */
    public boolean isMissingRequirements() throws GeneralException {
         return false;
    }

    public boolean isCurrentMitigation() throws GeneralException {
        // Calling inspectLastDecision() ensures that this is initialized.
        inspectLastDecision();
        return currentMitigation;
    }

    public boolean isExpiredMitigation() throws GeneralException {
        // Calling inspectLastDecision() ensures that this is initialized.
        inspectLastDecision();
        return expiredMitigation;
    }

    public Date getLastMitigationDate() throws GeneralException {
        // Calling inspectLastDecision() ensures that this is initialized.
        inspectLastDecision();
        return lastMitigationDate;
    }

    /**
     * Return a unique key that can identify the application on which this
     * entitlement reside (non-exception items will just return an empty
     * string). This will be put into the DOM as a class name so that we can
     * search for it.
     */
    public String getApplicationKey() {
        return "";
    }

    /**
     * UI bean which exposed the properties of a given profile under
     * certification. Used to populate profile details expando in
     * business role compositions certifications.
     */
    public static class ProfileBean extends CertificationItemBean {

        private RoleSnapshot.ProfileSnapshot profile;
        private List<String> contraintsDescription;

        /**
         * Default constructor
         */
        public ProfileBean() { super(); }


        /**
         * Creates a new instance given the objects required to
         * retrieve the profile details.
         *
         * @param bean The BaseBean creating this profile bean.
         * @param item The certification item for this profile.
         * @param entity The certification entity for this profile.
         *  Represents the parent business role.
         * @param workItem The ID of the work item in which this profile
         *  is being viewed. If null, the profile is being viewed from
         *  within the certification.
         *
         * @throws GeneralException
         */
        public ProfileBean(BaseBean bean, CertificationItem item, CertificationEntity entity, String workItem) throws GeneralException {
            super(bean, item, entity, workItem);

            RoleSnapshot snap = entity.getRoleSnapshot();
            if (snap != null){
                profile = snap.getProfileSnapshot(item.getTargetId());
            }
        }

        /**
         * @return Name of the bean's underlying profile.
         */
        public String getName(){
            String name = null;
            if (profile != null){
                name = profile.getObjectName();
                if (name == null){
                    Message msg = new Message(MessageKeys.TEXT_ENTITLEMENTS_ON_APP, profile.getApplication());
                    name = msg.getLocalizedMessage(getLocale(), null);
                }
            }

            return name;
        }
        
        /**
         * @return Name of the bean's profile. Make it friendly since profile names
         * do not have a lot of meaning for humans.
         */
        public String getDisplayableName(){
            String name = null;
            if (profile != null){
                Message msg = new Message(MessageKeys.PROFILE_FOR_NAMED_ENTITY, profile.getApplication());
                name = msg.getLocalizedMessage(getLocale(), null);
            }

            return name;
        }

        /**
         *
         * @return Application name for the bean's underlying profile.
         */
        public String getApplicationName(){
            return profile != null ? profile.getApplication() : null;
        }

        /**
         *
         * @return Description for the bean's underlying profile.
         */
        public String getDescription(){
            return profile != null ? profile.getObjectDescription(getLocale()) : null;
        }

        /**
         * @return Permissions for the bean's underlying profile.
         */
        public List<Permission> getPermissions(){
            return profile != null ? profile.getPermissions() : null;
        }

        /**
         * @return Constraint for the bean's underlying profile.
         */
        public List<Filter> getConstraints(){
            return profile != null ? profile.getConstraints() : null;
        }

        /**
         * Converts the profile's constraints into a human readable string.
         *
         * @return  Human readable string for the bean's underlying profile's constraints.
         */
        public List<String> getContraintsDescription() throws GeneralException{

            if (contraintsDescription == null){
                contraintsDescription = new ArrayList<String>();

                if (profile != null &&  profile.getConstraints() != null &&  !profile.getConstraints().isEmpty()){
                    Application app = this.getContext().getObjectByName(Application.class, profile.getApplication());
                    if (app != null && app.getSchema(Connector.TYPE_ACCOUNT) != null){
                        Schema appSchema = app.getSchema(Connector.TYPE_ACCOUNT);
                        for(Filter f : profile.getConstraints()){
                            contraintsDescription.add(getFilterText(f, appSchema));
                        }
                    }
                }
            }

            return contraintsDescription;
        }

        /**
         * Converts a filter into a human-readable string. Handles both
         * leaf and composite filters.
         *
         * @param filter Filter to convert to a string
         * @param appSchema Application schema for the given profile
         * @return  Human readable string for the bean's underlying profile.
         */
        private static String getFilterText(Filter filter, Schema appSchema){

             if (filter == null)
                return "";

             if (filter instanceof Filter.LeafFilter){
                return getLeafFilterText((Filter.LeafFilter)filter, appSchema);
             } else if (filter instanceof Filter.CompositeFilter) {
                 String filterText = "";
                 Filter.CompositeFilter cf = (Filter.CompositeFilter)filter;
                 int c = 0;
                 for (Filter child : cf.getChildren()){
                    if (c > 0)
                        filterText += " " + cf.getOperation() + " ";
                    filterText += getFilterText(child, appSchema);
                    c++;
                 }

                 return filterText;
             }

            return "";
        }

        /**
         * Converts a leaf filter into a human-readable string.
         *
         * @param filter Filter to convert to a string
         * @param appSchema Application schema for the given profile
         * @return Human readable string for the bean's underlying profile.
         */
        private static String getLeafFilterText(Filter.LeafFilter filter, Schema appSchema){

            if (filter == null)
                return "";

            String operation = filter.getOperation().getDisplayName();
            String values = "";
            if (filter.getValue()!=null){
                values = filter.getValue().toString();
                if (filter.isIgnoreCase())
                    values = values.toLowerCase();
                if (filter.getValue() instanceof String)
                    values = "'" + values + "'";
            }

            String propertyName = filter.getProperty();

            if (appSchema.getAttributeDefinition(propertyName) != null){
                String desc = appSchema.getAttributeDefinition(propertyName).getDescription();
                propertyName = desc != null ? desc : propertyName;
            }

            return propertyName + " " + operation + " " + values;
        }

        /**
         * @return True if no constraints exist for this profile.
         */
        public boolean isConstraintsEmpty(){
            return profile == null || profile.getConstraints() == null || profile.getConstraints().isEmpty();
        }

        /**
         * @return True if no permissions exist for this profile.
         */
        public boolean isPermissionsEmpty(){
            return profile == null || profile.getPermissions() == null || profile.getPermissions().isEmpty();
        }
    }


     public static class GenericCertifiableBean extends CertificationItemBean {

         private Snapshot snapshot;

        /**
         * Default constructor
         */
        public GenericCertifiableBean() { super(); }

        public GenericCertifiableBean(BaseBean bean, CertificationItem item, CertificationEntity entity,
                                      String workItem) throws GeneralException {
            super(bean, item, entity, workItem);
            snapshot = item.getSnapshot();
        }

         public String getTargetId(){
             return item.getTargetId();
         }

        public String getName(){
            return item.getTargetName();
        }

        public String getType(){
            return Internationalizer.getMessage(item.getType().getMessageKey(), getLocale());
        }

        public String getDescription() throws GeneralException{
            return snapshot != null ? snapshot.getObjectDescription(getLocale()) : null;
        }

    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Entitlement Bean
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Base bean for entitlement items.
     */
    public static abstract class EntitlementBean extends CertificationItemBean {

        protected boolean newEntitlement;


        public EntitlementBean() { super(); }

        public EntitlementBean(BaseBean bean, CertificationItem item,
                               CertificationEntity identity, String workItem)
            throws GeneralException {

            super(bean, item, identity, workItem);

            Meter.enterByName("calculateIsNewEntitlement");
            this.newEntitlement = calculateIsNewEntitlement(identity, item);
            Meter.exitByName("calculateIsNewEntitlement");
        }

        /**
         * Calculate whether this is a new entitlements since the last
         * certification report and perform any other exceptional entitlement
         * initialization.
         *
         * @param  identity  The CertificationIdentity for this entitlement.
         * @param  item      The CertificationItem for this entitlement.
         *
         * @return True if this is a new entitlement, false otherwise.
         */
        abstract boolean calculateIsNewEntitlement(CertificationEntity identity,
                                                   CertificationItem item)
            throws GeneralException;

        public boolean isNewEntitlement() {
            return newEntitlement;
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Business Role Bean
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * UI bean used to hold information about business role certification.
     */
    public static class BusinessRoleBean
        extends EntitlementBean
        implements sailpoint.web.identity.BusinessRoleBean
    {
        private sailpoint.web.identity.BusinessRoleBean delegate;
        private Map<String,Entitlements> bundleEntitlementsMap;
        Map<Bundle,List<EntitlementGroup>> nonFlattenedRoleMappings;
        private String accountIds;

        /**
         * Default constructor
         */
        public BusinessRoleBean() { super(); }

        /**
         *
         * @param  bean          The BaseBean creating this.
         * @param  item          The certification item for this role membership or child.
         * @param  entity        The certification entity for the role being certified.
         * @param  workItem      The ID of the work item in which this role
         *                       is being viewed.
         * @param nonFlattenedRoleMappings Identity entitlements granting role.
         * @throws GeneralException
         */
        public BusinessRoleBean(BaseBean bean, CertificationItem item,
                                CertificationEntity entity, String workItem,
                                Map<Bundle,List<EntitlementGroup>> nonFlattenedRoleMappings)
            throws GeneralException
        {
            super(bean, item, entity, workItem);
            this.nonFlattenedRoleMappings = nonFlattenedRoleMappings;
        }

        /**
         *
         * @param identity The Identity being certified.
         * @param item CertificationItem for the role certification.
         * @return True if the role has not been certified for the
         *  given identity. False if is not an identity or role membership certification.
         */
        @Override
        boolean calculateIsNewEntitlement(CertificationEntity identity,
                                          CertificationItem item)
        {
            return item.getHasDifferences();
        }

        /**
         * @return the business role bean delegate, creating if necessary.
         */
        private sailpoint.web.identity.BusinessRoleBean getDelegate()
            throws GeneralException {

            if (null == this.delegate) {
                Meter.enterByName("BusinessRoleBean create business role delegate");
                Bundle bundle = item.getBundle(super.getContext());
                Identity identity = item.getIdentity(super.getContext());

                // if the bundle has changed names or has been deleted, we will no longer be able
                // to retrieve it.
                if (bundle != null) {
                    // Don't calculate the non-flattened role mappings for now.
                    this.delegate =
                        new sailpoint.web.BusinessRoleBean(identity, bundle, item.getBundleEntitlements(),
                                this.nonFlattenedRoleMappings, getLoggedInUserName(), getContext());

                }
                Meter.exitByName("BusinessRoleBean create business role delegate");
            }

            return this.delegate;
        }

        public String getRoleId() throws GeneralException{
            return getDelegate() != null ? getDelegate().getId() : null;
        }

        /**
         * Returns identity's account IDs granting the entitlements for this business role.
         *
         * @param ctx A SailPointContext instance.
         * @return identity's account IDs granting the entitlements for this business role.
         * @throws GeneralException
         */
        private String calculateAccountIds(SailPointContext ctx)
            throws GeneralException {

            if (null != this.getEntitlements()) {
                CertificationEntity certId =
                    ctx.getObjectById(CertificationEntity.class, getCertificationIdentityId());
                if (null != certId) {
                    String idName = certId.getIdentity();
                    Set<String> ids = new HashSet<String>();
                    for (Entitlements es : this.getEntitlements()) {
                        String accountId =
                            ObjectUtil.getAccountId(ctx, idName,
                                                    es.getApplicationName(),
                                                    es.getInstance(),
                                                    es.getNativeIdentity());
                        ids.add(accountId);
                    }
                    this.accountIds = Util.listToCsv(new ArrayList<String>(ids), true);
                }
            }

            return this.accountIds;
        }

        /**
         * @return Map of the entitlements for the business role. Key is the role name.
         */
        public Map<String,? extends Entitlements> getBundleEntitlementsMap()
            throws GeneralException {
            if (null == this.bundleEntitlementsMap)
            {
                this.bundleEntitlementsMap = new HashMap<String,Entitlements>();
                if (null != this.getEntitlements())
                {
                    for (Entitlements es : this.getEntitlements())
                        this.bundleEntitlementsMap.put(es.getApplicationName(), es);
                }
            }
            return this.bundleEntitlementsMap;
        }

        /**
         * Get a comma-separated list of the account IDs of the application
         * accounts that grant this business role.
         *
         * @return A comma-separated list of the account IDs of the application
         *         accounts that grant this business role.
         */
        public String getAccountIds() throws GeneralException {
            if (null == this.accountIds) {
                Meter.enterByName("BusinessRoleBean calculate account IDs");
                this.accountIds = calculateAccountIds(super.getContext());
                Meter.exitByName("BusinessRoleBean calculate account IDs");
            }

            return this.accountIds;
        }

        /**
         * @return Return the description of the business role.
         */
        public String getDescription() throws GeneralException {
            return getDelegate() != null ? WebUtil.sanitizeHTML(getDelegate().getDescription()) : null;
        }

        /**
         * @return Business role name.
         */
        public String getName() throws GeneralException {
            return getDelegate() != null ? getDelegate().getName() : this.item.getBundle();
        }

        public String getIdentityId() {
            String identityId = null;
            try {
                identityId =  getDelegate() != null ? getDelegate().getIdentityId() : null;
            } catch (GeneralException e) {
                throw new RuntimeException(e);
            }
            return identityId;
        }


        public boolean isAssigned() {
            try {
                return getDelegate() != null ? getDelegate().isAssigned() : false;
            } catch (GeneralException e) {
                throw new RuntimeException(e);
            }
        }

        public boolean isMissingRequirements() throws GeneralException {
             return getDelegate() != null ? getDelegate().isMissingRequirements() : false;
        }

        /**
         * @return Entitlements that grant this business role for the identity.
         */
        public List<? extends Entitlements> getEntitlements() throws GeneralException {
            return getDelegate() != null ? getDelegate().getEntitlements() : null;
        }

        /**
         * Get the entitlements that grant each role in the hierarchy of this role.
         * This is similar to {@link #getEntitlements()}, but allows determining the
         * entitlements that grant each role in a hierarchy.
         *
         * @return A List of Entitlements that grant each role in the hierarchy that
         *         of this role.
         */
        public Map<Bundle, List<EntitlementGroup>> getEntitlementsByRole()
            throws GeneralException {
            return getDelegate() != null ? getDelegate().getEntitlementsByRole() : null;
        }

        /**
         * Return the roles granted by the given application, attribute, value
         * tuple.
         *
         * @param  app         The name of the application.
         * @param  attr        The name of the attribute or permission.
         * @param  val         The attribute value or permission right.
         * @param  permission  Whether the entitlement is a permission.
         *
         * @return The roles granted by the given application, attribute, value
         *         tuple.
         */
        public List<Bundle> getRolesForEntitlement(String app, String attr, String val, boolean permission)
            throws GeneralException {
            return getDelegate() != null ? getDelegate().getRolesForEntitlement(app, attr, val, permission) : null;
        }

        /**
         * Returns role type definition for this role
         * @return RoleTypeDefinition, may be null
         * @throws GeneralException
         */
        public RoleTypeDefinition getRoleTypeDefinition() throws GeneralException{
            return getDelegate() != null ? getDelegate().getRoleTypeDefinition() : null;
        }

    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Exception Bean
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * UI bean used to hold information about exceptional entitlement certification.
     */
    public static class ExceptionBean extends EntitlementBean
    {
        private String application;

        private EntitlementSnapshot exceptionEntitlements;

        // See getters for a description of these maps.
        private Map<String,Map<String,Boolean>> newAttrs;
        private Map<String,Map<String,Boolean>> newPerms;

        private Map<String,Application> applicationMap;

        private LinkSnapshot linkSnapshot;

        /**
         * The identity which is the target of this certification item.
         * Currently this applies to account group membership certifications.
         */
        private IdentityDTO identityBean;
        private IdentityBeanLight identityBeanLight;

        private boolean firstInGroup;
        private boolean lastInGroup;

        private Boolean componentOrComposite;

        /**
         * Map of entitlement descriptions, used to give descriptions to attribute
         * values.
         */
        private Map<String, Map<String,String>> descriptionMap;

        /**
         * List of icons that should be displayed
         * to the right of the account id. If configured
         * in the uiconfig.
         */
        private List<Icon> accountIcons;

        /**
         * A synthetic ID that exists to make the case-insensitive IE6 browser
         * happy. It is the application concatenated with the instance and
         * the native identity and a hash code to force case sensitivity
         */
        private String exceptionId;


        public ExceptionBean() { super(); }

        public ExceptionBean(BaseBean bean, CertificationItem item,
                             CertificationEntity entity, String workItem,
                             Map<String,Application> appMap)
            throws GeneralException
        {
            this(bean, item, entity, workItem, appMap, null, null, null);
        }

        /**
         *
         * @param bean
         * @param item
         * @param entity
         * @param workItem
         * @param appMap
         * @param lastNativeId
         * @param lastInstance
         * @param lastApp
         *
         * @throws GeneralException
         */
        public ExceptionBean(BaseBean bean, CertificationItem item,
                             CertificationEntity entity, String workItem,
                             Map<String,Application> appMap,
                             String lastNativeId,
                             String lastInstance,
                             String lastApp)
            throws GeneralException
        {
            super(bean, item, entity, workItem);

            Meter.enterByName("ExceptionBean constructor");
            this.exceptionEntitlements = item.getExceptionEntitlements();

            // Bug #4684: Something is causing a lazy initialization exception
            // from delegated work items occasionally.  I can't reproduce, but
            // am adding the load() to try to alleviate.  I can't figure out
            // why this would be necessary unless something is hanging on to
            // the CertificationItemBean in the session.
            this.exceptionEntitlements.load();

            this.application = this.exceptionEntitlements.getApplication();

            this.applicationMap = appMap;

            String nativeId = this.getNativeIdentity();
            String instance = this.getInstance();
            this.firstInGroup =
                ((null != this.application) && !this.application.equals(lastApp)) ||
                ((null != instance) && !instance.equals(lastInstance)) ||
                ((null != nativeId) && !nativeId.equals(lastNativeId));

            StringBuffer idWithoutHash = new StringBuffer();
            if(getLinkSnapshot()!=null) {
                String snapshotApp = getLinkSnapshot().getApplication();
                if (snapshotApp != null)
                    idWithoutHash.append(snapshotApp);
            }
            idWithoutHash.append("_");
            if (instance != null)
                idWithoutHash.append(instance);
            idWithoutHash.append("_");
            if (nativeId != null)
                idWithoutHash.append(nativeId);
            String refinedIdWithoutHash =
                WebUtil.escapeHTMLElementId(WebUtil.escapeJavascript(idWithoutHash.toString()));
            int hash = refinedIdWithoutHash.hashCode();
            exceptionId = refinedIdWithoutHash + '_' + hash;

            Meter.exitByName("ExceptionBean constructor");
        }

        public String getExceptionId() {
            return this.exceptionId;
        }

        public EntitlementSnapshot getExceptionEntitlements() {
            return this.exceptionEntitlements;
        }

        public Map<String,Application> getApplicationMap()
        {
            return this.applicationMap;
        }

        @Override
        boolean calculateIsNewEntitlement(CertificationEntity identity,
                                          CertificationItem item)
            throws GeneralException {

            this.newAttrs = new HashMap<String,Map<String,Boolean>>();
            this.newPerms = new HashMap<String,Map<String,Boolean>>();

            IdentityDifference idDiff = identity.getDifferences();
            if (null != idDiff) {
                item.calculateExceptionDifferences(idDiff, newAttrs, newPerms);
            }

            return !this.newAttrs.isEmpty() || !this.newPerms.isEmpty();
        }

        /**
         * Return a structure that contains information about which attribute
         * values are new since the last certification was completed. This is
         * returned as a nested Map structure because the expression language in
         * JSF does not support passing parameters to methods, but does support
         * retrieving values by key from maps. The returned value is a Mapping
         * of attribute name -> Map of attribute value / is value new flag.  As
         * an example:
         * <pre>
         *   Groups ->
         *             Domain Admins -> true
         *             Users -> true
         *   Sandwiches ->
         *             Ham -> true
         *             Club -> true
         * </pre>
         */
        public Map<String,Map<String,Boolean>> getNewAttributeMap()
        {
            return this.newAttrs;
        }

        /**
         * Return a mapping of target -> right / is right new flag.
         * See javadoc for getNewAttributeMap() for a full description.
         */
        public Map<String,Map<String,Boolean>> getNewPermissionMap()
        {
            return this.newPerms;
        }

        public String getApplication()
        {
            return application;
        }
        
        public String getApplicationDescription() throws GeneralException {
            Application app = (this.applicationMap == null) ? null : this.applicationMap.get(this.application);
            return (app == null) ? null : app.getDescription(getLocale());
        }
        
        public String getAccountId() {
            // Using this to cache the value.  Pretty stupid ... get rid of this.
            ValueBinding vb =
                baseBean.getFacesContext().getApplication().createValueBinding("#{accountId}");
            AccountIdBean aib =
                (AccountIdBean) vb.getValue(baseBean.getFacesContext());


            // jsl - refactored this a bit so that AccountIdBean can
            // handle a template instance identifier

            String acctId = aib.getAccountId(super.certificationIdentityName,
                                             this.application,
                                             this.getInstance(),
                                             this.getNativeIdentity());
            return acctId;
        }

        public String getNativeIdentity() {
            return (null != this.exceptionEntitlements)
                ? this.exceptionEntitlements.getNativeIdentity() : null;
        }

        public String getInstance() {
            return (null != this.exceptionEntitlements)
                ? this.exceptionEntitlements.getInstance() : null;
        }

        public LinkSnapshot getLinkSnapshot() throws GeneralException {

            if (null == this.linkSnapshot) {
                String instance = this.getInstance();
                String nativeIdentity = this.getNativeIdentity();
                if ((null != nativeIdentity) && (null != this.application)) {
                    IdentitySnapshot idSnap = this.entity.getIdentitySnapshot(getContext());
                    if (null != idSnap) {
                        this.linkSnapshot = idSnap.getLink(this.application, instance, nativeIdentity);
                    }
                }
            }

            // if we haven't found the snapshot, try and get the link. This is useful in situations where
            // we don't have a snapshot, such as an account group membership certification
            if (linkSnapshot == null && exceptionEntitlements != null){
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.and(Filter.ignoreCase(Filter.eq("nativeIdentity", exceptionEntitlements.getNativeIdentity())),
                        Filter.eq("application.name", exceptionEntitlements.getApplicationName())));
                List<Link> links = getContext().getObjects(Link.class, ops);

                if (links != null && !links.isEmpty()){
                    Link link =  links.get(0);
                    LinkSnapshot snap = new LinkSnapshot();
                    snap.setApplication(link.getApplicationName());
                    snap.setId(link.getId());
                    snap.setAttributes(link.getAttributes());
                    snap.setNativeIdentity(link.getNativeIdentity());
                    linkSnapshot = snap;
                }
            }

            return this.linkSnapshot;
        }

        /**
         * Gets the identity targets by this certification item. This is only relevant
         * in certifications where the item represents an identity rather than an
         * entitlement, such as Account group membership certifications.
         *
         * @return Identity retrieved from the link, or null if not found.
         * @throws GeneralException
         */
        public IdentityDTO getIdentityBean() throws GeneralException {

            if (this.identityBean == null) {
                Identity theIdentity = fetchIdentity();
                if (theIdentity != null) {
                    this.identityBean = new IdentityDTO(theIdentity);
                }
            }
            return this.identityBean;
         }

        public IdentityBeanLight getIdentityBeanLight() throws GeneralException {

            if (this.identityBeanLight == null) {
                Identity theIdentity = fetchIdentity();
                if (theIdentity != null) {
                    this.identityBeanLight = new IdentityBeanLight(theIdentity);
                }
            }
            return this.identityBeanLight;
        }

        private Identity fetchIdentity() throws GeneralException {

            if (CertificationEntity.Type.AccountGroup.equals(item.getParent().getType()) &&
                     exceptionEntitlements != null){
                 QueryOptions ops = new QueryOptions();
                 ops.add(Filter.and(Filter.ignoreCase(Filter.eq("nativeIdentity", exceptionEntitlements.getNativeIdentity())),
                         Filter.eq("application.name", exceptionEntitlements.getApplicationName())));
                 List<Link> links = getContext().getObjects(Link.class, ops);
                 if (links != null && !links.isEmpty()) {
                     return links.get(0).getIdentity();
                 } else {
                     return null;
                 }
             } else if (CertificationEntity.Type.DataOwner.equals(this.item.getParent().getType())) {
                 return getContext().getObjectById(Identity.class, this.item.getTargetId());
             } else{
                 return this.item.getIdentity(getContext());
             }
        }

        public boolean isFirstInGroup() {
            return firstInGroup;
        }

        public boolean isLastInGroup() {
            return lastInGroup;
        }

        public void setLastInGroup(boolean b) {
            this.lastInGroup = b;
        }

        public boolean isShowAccountDetails() {
            return !isIIQEntitlement();
        }

        public boolean isShowApplicationDetails() {
            return !isIIQEntitlement();
        }

        private boolean isIIQEntitlement() {
            return Certification.IIQ_APPLICATION.equals(this.application);
        }

        @Override
        public String getApplicationKey() {
            return WebUtil.getApplicationKey(this.getApplication(), this.getInstance(),
                                             this.getNativeIdentity());
        }

        public boolean isComponentOrCompositeLink() throws GeneralException {

            if (null == this.componentOrComposite) {

                this.componentOrComposite = false;

                SailPointContext ctx = baseBean.getContext();

                Identity identity =
                    ctx.getObjectByName(Identity.class, super.certificationIdentityName);
                if (null != identity) {
                    Link link =
                        identity.getLink(this.applicationMap.get(this.application),
                                         this.getInstance(), this.getNativeIdentity());

                    // True if this is a composite.
                    if (null != link) {
                        if (null != link.getComponentIds()) {
                            this.componentOrComposite = true;
                        }
                        else {
                            // True if this is a component of a composite.
                            Link owner = identity.getOwningCompositeLink(link);
                            if (null != owner) {
                                this.componentOrComposite = true;
                            }
                        }
                    }
                }
            }

            return this.componentOrComposite;
        }

        /**
         * @return Map of attributeValue descriptions.
         */
        public Map<String, Map<String,String>> getEntitlementDescriptionsMap()
            throws GeneralException {

            if (null == this.descriptionMap) {
                this.descriptionMap = assembleEntitlementDescriptions(item);
            }

            return this.descriptionMap;
        }

        /**
         * For each certification item build a map of entitlement descriptions using application
         * specific message catalog files.
         */
        public Map<String, Map<String, String>> assembleEntitlementDescriptions(CertificationItem item)
            throws GeneralException {

            Meter.enterByName("Calculating entitlement descriptions");
            Map<String, Map<String, String>> descriptionMap = new HashMap<String, Map<String, String>>();

            // Allow a setting in the SystemConfiguration that will allow us to disable
            // The cost of looking up these messages.
            boolean disabled = false;
            Configuration syscon = Configuration.getSystemConfig();
            if (syscon != null) {
                disabled = syscon.getBoolean("certificationEntitlementDescriptionsDisabled");
            }
            if ( disabled )  return descriptionMap;

            EntitlementSnapshot es = item.getExceptionEntitlements();
            if ( es == null ) {
                return descriptionMap;
            }

            String appName = es.getApplication();
            // NOTE: once we have more then account and group this will
            // likely have to change
            CertificationEntity entity = item.getParent();
            @SuppressWarnings("unused")
            String objectType = Connector.TYPE_ACCOUNT;
            if ( !CertificationEntity.Type.Identity.equals(entity.getType()) ) {
                objectType = Connector.TYPE_GROUP;
            }

            Application app = this.applicationMap.get(appName);

            Attributes<String,Object> extraAttrs = es.getAttributes();
            if ((extraAttrs != null) && !extraAttrs.isEmpty()) {
                descriptionMap = Explanator.getDescriptions(app, extraAttrs, getLocale());
            }

            List<Permission> perms = es.getPermissions();
            if(perms!=null && !perms.isEmpty()) {
                Map<String, String> permsMap = new HashMap<String, String>();
                for(Permission perm : perms) {
                    if(perm.getTarget()!=null) {
                        String desc = Explanator.getPermissionDescription(app, perm.getTarget(), getLocale());
                        permsMap.put(perm.getTarget(), desc);
                    }
                }

                // TODO: This is a possibility legitimate use of PERMISSION_ATTRIBUTE,
                // I'm guessing we just need some special map key to hold another
                // sub-map of target explanations.  Since targets can have the same name
                // as attribute values, we need to keep them in different maps - jsl
                descriptionMap.put(ManagedAttribute.OLD_PERMISSION_ATTRIBUTE, permsMap);
            }

            Meter.exitByName("Calculating entitlement descriptions");

            return descriptionMap;
        }

        public List<Icon> getAccountIcons() throws GeneralException {

            if (null == this.accountIcons) {
                this.accountIcons = calculateAccountIcons();
            }

            return this.accountIcons;
        }

        /**
         * Use the UIConfig object in combination with the
         * link configuration to build the icons that should
         * be displayed to the right of the accountid.
         *
         */
        public List<Icon> calculateAccountIcons()
            throws GeneralException {

            Meter.enterByName("Calculating icons");
            Iconifier iconifier = new Iconifier();
            List<Icon> icons = iconifier.getAccountIcons(item);
            Meter.exitByName("Calculating icons");
            return icons;
        }

        public static class IdentityBeanLight {

            private String objectId;
            private String displayableName;

            public IdentityBeanLight(Identity identity) {
                this.objectId = identity.getId();
                this.displayableName = identity.getDisplayableName();
            }

            public String getObjectId() {
                return this.objectId;
            }
            public void setObjectId(String objectId) {
                this.objectId = objectId;
            }
            public String getDisplayableName() {
                return this.displayableName;
            }
            public void setDisplayableName(String displayableName) {
                this.displayableName = displayableName;
            }

        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Violation Bean
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * UI bean used to hold information about PolicyViolations.
     * PolicyViolation is already a decent string returning bean,
     * so we will use it directly.
     */
    public static class ViolationBean extends EntitlementBean
    {
        private ViolationViewBean violationViewBean;


        public ViolationBean() { super(); }

        public ViolationBean(BaseBean bean, CertificationItem item,
                             CertificationEntity entity, String workItem)
            throws GeneralException
        {
            super(bean, item, entity, workItem);
        }

        /**
         * The violation bean that has information for display.
         */
        public ViolationViewBean getViolation() throws GeneralException {
            if (null == this.violationViewBean) {
                Meter.enterByName("PolicyViolationBean get violations");
                PolicyViolation policyViolation = item.getPolicyViolation();
                if (policyViolation != null)
                    this.violationViewBean = new ViolationViewBean(baseBean.getContext(), policyViolation);
                Meter.exitByName("PolicyViolationBean get violations");
            }

            return this.violationViewBean;
        }

        /**
         * The description for this CertificationItemBean. One of the facelets
         * include files requires a top-level property on this bean that can
         * return a description.
         */
        public String getDescription() throws GeneralException {
            return (null != getViolation()) ? getViolation().getConstraint() : null;
        }

        /**
         * Returns True if the entitlements causing the violations are new, for example, have
         *  not been certified before.
         *
         * @param identity Identity in violations
         * @param item CertificationItem for the violation
         * @return  True if the entitlements causing the violations are new.
         * @throws GeneralException
         */
        @Override
        boolean calculateIsNewEntitlement(CertificationEntity identity,
                                          CertificationItem item)
            throws GeneralException {

            return item.getHasDifferences();
        }

        /**
         * Calculate the list of item decision choices. A violation provides
         * different options than entitlements.
         *
         * Do not allow remediating policy violations for now on non-sod policies
         */
        @Override
        List<SelectItem> calculateStatusChoices(Role role) throws GeneralException {
            List<SelectItem> list = new ArrayList<SelectItem>();
            //list.add(new SelectItem("", "-- Select Decision --"));

            list.add(new SelectItem(CertificationAction.Status.Mitigated, "mitigateRadio"));

            list.add(new SelectItem(CertificationAction.Status.Remediated, "revokeRadio"));

            CertificationDefinition certDefinition = item.getCertification().getCertificationDefinition(getContext());

            if (certDefinition == null) {
                certDefinition = new CertificationDefinition();
                certDefinition.initialize(getContext());
            }

            boolean delegationEnabled = certDefinition.isAllowEntityDelegation(getContext());

            // Don't allow delegating from within an item-delegation work item.
            if (!role.isViewingItemWorkItem && delegationEnabled) {
                list.add(new SelectItem(CertificationAction.Status.Delegated, "delegateRadio"));
            }

            return list;
        }

        @Override
        boolean calculateShowRemediationDialog(CertificationItem item,
                                               CertificationEntity entity,
                                               String workItemId) {

            // Policy violations always need this so that the certifier can
            // choose which roles to get rid of.
            return true;
        }

        /**
         * Override to return the text we want to show with a violation.
         */
        @Override
        String getStatusText(Status status) {
            String text = super.getStatusText(status);
            switch (status) {
            case Mitigated:
                text = "Violation Allowed";
                break;
            case Remediated:
                text = "Violation Corrected";
                break;
            }
            return text;
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Helper Inner Classes
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * A Map Loader that returns applications given an application name.
     */
    public static class ApplicationLoader implements LoadingMap.Loader<Application>
    {
        public Application load(Object key) throws GeneralException
        {
            SailPointContext ctx = SailPointFactory.getCurrentContext();
            if (key == null) {
                return null;
            }
            return ctx.getObjectByName(Application.class, key.toString());
        }
    }

    /**
     * A helper class that holds the current user's relation to an entitlement
     * and the context in which they are viewing the entitlement.  This is used
     * to help figure out whether an entitlement is read-only, how it should be
     * displayed on the current page, etc..  All properties are public, but
     * should be considered read-only.
     */
    private static class Role {
        boolean isItemActionActor;
        boolean isItemDelegationRequester;
        boolean isCertifierItemDelegationRequester;
        boolean isItemDelegationOwner;
        boolean isIdentityDelegationRequester;
        boolean isIdentityDelegationOwner;
        boolean isCertificationOwner;
        boolean isViewingCertification;
        boolean isViewingItemWorkItem;
        boolean isViewingIdentityWorkItem;
        boolean wasItemDecidedDuringIdentityDelegation;
        boolean wasItemDecidedOutsideOfIdentityDelegation;

        /**
         * Constructor.
         */
        public Role(BaseBean baseBean, CertificationItem item,
                    CertificationEntity identity, String workItem)
            throws GeneralException {

            Meter.enterByName("CertificationItemBean.Role constructor");
            CertificationAction action = item.getAction();
            CertificationDelegation itemDel = item.getDelegation();
            CertificationDelegation identityDel = identity.getDelegation();
            Certification cert = identity.getCertification();

            SailPointContext ctx = baseBean.getContext();
            Identity loggedInUser = baseBean.getLoggedInUser();

            Identity itemActionActor = (null != action) ? action.getActor(ctx) : null;
            Identity itemDelActor = (null != itemDel) ? itemDel.getActor(ctx) : null;
            Identity identityDelActor = (null != identityDel) ? identityDel.getActor(ctx) : null;
            String itemOwner = (null != itemDel) ? itemDel.getOwnerName() : null;
            String identityOwner = (null != identityDel) ? identityDel.getOwnerName() : null;

            // Owners get reset when forwarded.
            List<String> certifiers = cert!=null ? cert.getCertifiers() : null;

            isCertificationOwner = CertificationAuthorizer.isCertifier(loggedInUser, certifiers);
            // If not the owner, a "certification admin" should be treated as an
            // owner (ie - have full read/write access).
            if (!isCertificationOwner) {
                isCertificationOwner = CertificationAuthorizer.isCertificationAdmin(baseBean);
            }

            // Allow owners of parents of reassignments act as owners.
            if (!isCertificationOwner) {
                isCertificationOwner =
                    CertificationAuthorizer.isReassignmentParentOwner(loggedInUser, cert);
            }

            isItemDelegationOwner = usersEqual(itemOwner, loggedInUser);
            isIdentityDelegationOwner = usersEqual(identityOwner, loggedInUser);

            // Actors don't get reset when forwarded (we retain them for
            // auditing), so we might have to look through the work item owner
            // history to see if the actor was a previous owner.
            isItemActionActor =
                usersEqual(itemActionActor, loggedInUser) ||
                hasBuckBeenPassed(ctx, action, cert, loggedInUser);
            isItemDelegationRequester =
                usersEqual(itemDelActor, loggedInUser) ||
                hasBuckBeenPassed(ctx, itemDel, cert, loggedInUser);
            isIdentityDelegationRequester =
                usersEqual(identityDelActor, loggedInUser) ||
                hasBuckBeenPassed(ctx, identityDel, cert, loggedInUser);

            // Check if this item was delegated by the certifier.
            isCertifierItemDelegationRequester =
                ((null != itemDel) && (null == itemDel.getActingWorkItem()));

            // Figure out if an item was decided outside of an identity
            // delegation.
            boolean actionOccurredInCert =
                ((null != action) && (null == action.getActingWorkItem()));
            wasItemDecidedDuringIdentityDelegation =
                item.wasDecidedInIdentityDelegationChain(identityDel);
            wasItemDecidedOutsideOfIdentityDelegation =
                actionOccurredInCert ||
                ((null != action) && !wasItemDecidedDuringIdentityDelegation);

            isViewingCertification = (null == workItem);
            isViewingItemWorkItem = isViewingWorkItem(workItem, itemDel);
            isViewingIdentityWorkItem = isViewingWorkItem(workItem, identityDel);

            Meter.exitByName("CertificationItemBean.Role constructor");
        }

        private static boolean usersEqual(String user1Name, Identity user2) {
            if ((null != user1Name) && (null != user2)) {
                return user1Name.equals(user2.getName());
            }
            return false;
        }

        private static boolean usersEqual(Identity user1, Identity user2) {
            if ((null != user1) && (null != user2)) {
                return user1.equals(user2);
            }
            return false;
        }

        /**
         * Check to see if the given monitor was acted upon in a work item that
         * is now owned by the given loggedInUser.
         */
        private boolean hasBuckBeenPassed(SailPointContext ctx,
                                          WorkItemMonitor monitor,
                                          Certification cert,
                                          Identity loggedInUser)
            throws GeneralException {

            if (null != monitor) {
                Identity actor = monitor.getActor(ctx);
                if (null != actor) {

                    List<WorkItem> items = null;
                    String actingWorkItemId = monitor.getActingWorkItem();
                    if (null != actingWorkItemId) {
                        WorkItem item =
                            ctx.getObjectById(WorkItem.class, actingWorkItemId);
                        if (null != item) {
                            items = new ArrayList<WorkItem>();
                            items.add(item);
                        }
                    }
                    else {
                        // No acting work item - this was done in the cert.
                        items = cert != null ? cert.getWorkItems() : null;
                    }

                    if (null != items) {
                        // Buck has been passed if the logged in user is the
                        // current owner of the work item and the actor is in
                        // the owner history.
                        for (WorkItem item : items) {
                            if (loggedInUser.equals(item.getOwner()) &&
                                identityInOwnerHistory(actor, item.getOwnerHistory())) {
                                return true;
                            }
                        }
                    }
                }
            }

            return false;
        }

        /**
         * Check if the given actor was once an owner in the given owner history
         * list.
         */
        private boolean identityInOwnerHistory(Identity actor,
                                               List<WorkItem.OwnerHistory> history) {

            if ((null != actor) && (null != history)) {
                for (WorkItem.OwnerHistory historyEntry : history) {
                    if (actor.getDisplayName().equals(historyEntry.getOldOwnerDisplayName())) {
                        return true;
                    }
                }
            }

            return false;
        }

        /**
         * Return whether or not we are currently viewing the work item for the
         * given delegation.
         *
         * @param  workItem    The ID of the work item currently being viewed.
         * @param  delegation  The delegation to compare against.
         *
         * @return True if we are currently viewing the work item for the given
         *         delegation, false otherwise.
         */
        private static boolean isViewingWorkItem(String workItem,
                                                 CertificationDelegation delegation) {
            String delWorkItem = (null != delegation) ? delegation.getWorkItem() : null;
            return (null != delWorkItem) && delWorkItem.equals(workItem);

        }
    }
}
