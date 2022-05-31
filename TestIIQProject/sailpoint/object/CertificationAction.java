/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An object that represents an certification decision.
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.commons.lang3.builder.ToStringBuilder;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Localizable;
import sailpoint.tools.Message;
import sailpoint.tools.MessageKeyHolder;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * An object that represents an certification decision.
 * These can be stored on both CertificationEntity and
 * CertificationItem, though currently the UI only exposes
 * actions on items.
 *
 * A null action implies that the item is still "open".
 * 
 * The class extends WorkItemMonitor whose fields are used
 * only if the action type is Remediate.
 * 
 */
@XMLClass
public class CertificationAction extends WorkItemMonitor
{
    //////////////////////////////////////////////////////////////////////
    //
    // Status
    //
    //////////////////////////////////////////////////////////////////////

    private static final long serialVersionUID = 7221912195553672246L;

    public static String CERT_BULK_DECISION_REASSIGN = "Reassign";

    /**
     * The states an certification item might be in.
     *
     * Approved indicates that the item was unconditionally approved.
     *
     * Mitigated indicates that the item was approved, but with
     * conditions. A WorkItem can be assigned to to track the
     * mitigation process.
     *
     * Remediated indicates that the item was rejected, and a 
     * remediation process was launched. A WorkItem can be assigned
     * to track the remediation process.
     *
     * Delegated is not used in the persistent model, it is only used
     * at runtime when calculating status summaries for the UI. In the model,
     * an item considered delegated if it has a non-null CertificationDelegation
     * object. For UI convenience the presence of this object is converted
     * to the Delegated state by *insert method name here*.
     * 
     * Similarly, RevokeAccount is not used in the persistence model, but only
     * at runtime in the UI. In the CertificationAction, a revoke account is
     * modeled as a Remediated status with the revokeAccount flag set to true.
     *
     * The messageKey is used in certifications and reports to 
     * convey the decision that was made and it will be past tense
     * (Approved, Revoked, etc.)
     *
     * The promptKey is used in the UI when *making* the decision and
     * it will be present tense (Approve, Revoke), etc.
     * 
     */
    @XMLClass(xmlname="CertificationStatus")
    public enum Status implements MessageKeyHolder, Localizable {

        Approved("Approved", "cert_action_approved", "cert_action_approve"),
        Mitigated("Allowed", "cert_action_mitigated", "cert_action_mitigate"),
        Remediated("Remediated", "cert_action_remediated", "cert_action_remediate"),
        Acknowledged("Acknowledged", "cert_action_acknowledged", "cert_action_acknowledge"),
        Cleared("Cleared", "cert_action_cleared", "cert_action_clear"),

        // runtime only statuses
        Delegated("Delegated", "cert_action_delegated", "cert_action_delegate"),
        RevokeAccount("Revoke Account", "cert_action_revoke_account", "cert_action_to_revoke_account"),

        // status to use when remediated for modifiable entitlement. 
        // used only for reports currently 
        Modified("Modified", "cert_action_remedation_modified"),

        ApproveAccount("Approve Account", "cert_action_approved_account", "cert_action_to_approve_account"),
        AccountReassign("Account Reassign", "cert_action_account_reassigned", "cert_action_to_reassign_account");

        private String displayName;
        private String messageKey;
        private String promptKey;

        Status(String displayName, String messageKey)
        {
            this.displayName = displayName;
            this.messageKey = messageKey;
        }

        Status(String displayName, String messageKey, String promptKey)
        {
            this.displayName = displayName;
            this.messageKey = messageKey;
            this.promptKey = promptKey;
        }

        /**
         * @deprecated  Use {@link #getMessageKey()} for i18n.
         */
        public String getDisplayName() {
            return this.displayName;
        }

        public void setDisplayName(String name) {
            this.displayName = name;
        }

        public String getMessageKey() {
            return this.messageKey;
        }

        public void setMessageKey(String key) {
            this.messageKey = key;
        }

        public String getPromptKey() {
            return this.promptKey;
        }

        public void setPromptKey(String key) {
            this.promptKey = key;
        }

        public String getLocalizedMessage() {
            return getLocalizedMessage(Locale.getDefault(), TimeZone.getDefault());
        }

        public String getLocalizedMessage(Locale locale, TimeZone timezone) {
            Message msg = new Message(getMessageKey());
            return msg.getLocalizedMessage(locale, timezone);
        }
    }

    /**
     * The actions that can occur in response to a remediation request.
     */
    @XMLClass
    public static enum RemediationAction implements MessageKeyHolder {

        OpenWorkItem("Work Item", "cert_remed_action_work_item"),
        OpenTicket("Ticket", "cert_remed_action_ticket"),
        SendProvisionRequest("Provision Request", "cert_remed_action_send_prov_request"),
        NoActionRequired("Previously Completed", "cert_remed_action_no_action_required");
        
        private String displayName;
        private String messageKey;
        
        private RemediationAction(String displayName)
        {
            this.displayName = displayName;
        }

        private RemediationAction(String displayName, String messageKey)
        {
            this.displayName = displayName;
            this.messageKey = messageKey;
        }

        /**
         * @deprecated  Use {@link #getMessageKey()} for i18n.
         */
        public String getDisplayName()
        {
            return this.displayName;
        }
        
        public String getMessageKey() 
        {
            return this.messageKey;
        }
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The status of this item set by the certifier.
     */
    Status _status;

    /**
     * The date at which the decision was made.
     */
    Date _decisionDate;
    
    /**
     * The ID of the certification in which the decision was made. This is
     * important to maintain for auditing purposes when the decision is made
     * in a child certification that is later rescind (or assimilated) back
     * into the parent.
     */
    String _decisionCertificationId;
    
    /**
     * Flag indicating that the delegated action has been reviewed.
     */
    boolean _reviewed;

    /**
     * Flag indicating whether this item was bulk certified.
     */
    boolean _bulkCertified;

    /**
     * For mitigations, the date at which the conditional approval expires.
     * This is stored in the archive.
     * 
     * Note that we also inherit an _expiration field from WorkItemMonitor. 
     * That one is intended to be used only when generating a new WorkItem.
     * We could overload it for mitigations since work items will not exist
     * but make it clearer in case the work item
     * construction fields need to be factored out.
     */
    Date _mitigationExpiration;

    /**
     * The remediation action that is specified remediation request.  This
     * indicates how the remediation request should be handled.
     */
    RemediationAction _remediationAction;

    /**
     * For remediations, the provisioning plan that will be used to modify or
     * remove the accounts.
     */
    ProvisioningPlan _remediationDetails;

    /**
     * For remediations, this indicates that the entire account should be
     * revoked rather than just an individual entitlement. This has special
     * semantics since the account revocation can affect multiple items.
     */
    boolean _revokeAccount;
    
    /**
     * Whether this action is ready to have the remediation action kicked off.
     * This is a temporary flag used in the two-phase remediation launching
     * process of marking the remediations that need to be kicked off, then
     * handling all of them.
     */
    boolean readyForRemediation;
    
    /**
     * Whether the requested remediation action has been kicked off or not.
     * This only says whether the action has been requested, not whether it has
     * been completed.
     */
    boolean remediationKickedOff;

    /**
     * Whether the requested remediation action has been completed or not. This
     * is different than the completion state of the WorkItemMonitor. If this
     * is true, re-aggregation has occurred and determined that the access
     * that was to be removed no longer exists.
     */
    boolean remediationCompleted;

    /**
     * Plan containing additional object requests to be executed with the primary plan.
     * This includes the provisioning of missing required roles as well as revoking
     * the permits or requirements of a revoked role. 
     */
    private ProvisioningPlan _additionalActions;

    /**
     * List of child actions that are created by or
     * considered a part of this action.
     */
    private List<CertificationAction> _childActions;

    /**
     * List of parent actions that either created or
     * are dependent on this action.
     */
    private List<CertificationAction> _parentActions;

    /**
     * The CertificationAction which created this action. Common
     * example would be a role revocation created when a user
     * remediates a Role SOD Violation.
     */
    private CertificationAction _sourceAction;

    /**
     * Whether this action is the result of an automated decision.
     */
    private boolean _autoDecision;


    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public CertificationAction() {
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * This setter should only be used by persistence frameworks.  Callers
     * should instead use approve(), mitigate(Date, String),
     * remediate(String,String,String), or bulkApprove() so that state is
     * stored appropriately.
     * 
     * @deprecated Use approve(), etc... to set the action.
     */
    @XMLProperty
    public void setStatus(Status s) {
        _status = s;
    }

    public Status getStatus() {
        return _status;
    }

    /**
     * @deprecated Use approve(), etc... to set the decision date.
     */
    @XMLProperty
    public void setDecisionDate(Date date) {
        _decisionDate = date;
    }

    public Date getDecisionDate() {
        return _decisionDate;
    }

    /**
     * @deprecated Use approve(), etc... to set the decision certification ID.
     */
    @XMLProperty
    public void setDecisionCertificationId(String decisionCertificationId) {
        _decisionCertificationId = decisionCertificationId;
    }

    public String getDecisionCertificationId() {
        return _decisionCertificationId;
    }

    @XMLProperty
    public void setReviewed(boolean b) {
        _reviewed = b;
    }

    /**
     * Flag indicating that the delegated action has been reviewed.
     */
    public boolean isReviewed() {
        return _reviewed;
    }

   /**
    * This setter should only be used by persistence frameworks.
    * 
    * @deprecated Use bulkApprove() instead.
    */
    @XMLProperty
    public void setBulkCertified(boolean b) {
        _bulkCertified = b;
    }

    /**
     * Flag indicating whether this item was bulk certified.
     */
    public boolean isBulkCertified() {
        return _bulkCertified;
    }

    /**
     * This setter should only be used by persistence frameworks.
     * 
     * @deprecated Use mitigate() instead.
     */
    @XMLProperty
    public void setMitigationExpiration(Date d) {
        _mitigationExpiration = d;
    }

    /**
     * For mitigations, the date at which the conditional approval expires.
     */
    public Date getMitigationExpiration() {
        return _mitigationExpiration;
    }

    /**
     * This setter should only be used by persistence frameworks.
     * 
     * @deprecated Use remediate() instead.
     */
    @XMLProperty
    public void setRemediationAction(RemediationAction action) {
        _remediationAction = action;
    }

    /**
     * The remediation action that is specified in the remediation request. This
     * indicates how the remediation request should be handled.
     */
    public RemediationAction getRemediationAction() {
        return _remediationAction;
    }

    @XMLProperty(xmlname="RemediationPlan")
    public void setRemediationDetails(ProvisioningPlan plan) {
        _remediationDetails = plan;
    }

    /**
     * For remediations, the provisioning plan that will be used to modify or
     * remove the accounts.
     */
    public ProvisioningPlan getRemediationDetails() {
        return _remediationDetails;
    }

    /**
     * For remediations, this indicates that the entire account should be
     * revoked rather than just an individual entitlement. This has special
     * semantics since the account revocation can affect multiple items.
     */
    @XMLProperty
    public boolean isRevokeAccount() {
        return _revokeAccount;
    }
    
    public void setRevokeAccount(boolean revokeAccount) {
        _revokeAccount = revokeAccount;
    }

    @XMLProperty
    public boolean isReadyForRemediation() {
        return this.readyForRemediation;
    }
    
    public void setReadyForRemediation(boolean readyForRemediation) {
        this.readyForRemediation = readyForRemediation;
    }
    
    @XMLProperty
    public void setRemediationKickedOff(boolean remediationKickedOff) {
        this.remediationKickedOff = remediationKickedOff;
    }

    /**
     * True when the requested remediation action has been kicked off.
     * This only indicates that the action has been requested, not whether it has
     * been completed.
     */
    public boolean isRemediationKickedOff() {
        return remediationKickedOff;
    }

    @XMLProperty
    public void setRemediationCompleted(boolean remediationCompleted) {
        this.remediationCompleted = remediationCompleted;
    }

    /**
     * True when the requested remediation action has been completed. This
     * is different than the completion state of the WorkItemMonitor. If this
     * is true, re-aggregated has occurred and determined that the access
     * that was to be removed no longer exists.
     */
    public boolean isRemediationCompleted() {
        return remediationCompleted;
    }

    /**
     * Plan containing additional object requests to be executed with the primary plan.
     * This includes the provisioning of missing required roles as well as revoking
     * the permits or requirements of a revoked role. 
     */
    @XMLProperty
    public ProvisioningPlan getAdditionalActions() {
        return _additionalActions;
    }

    public void setAdditionalActions(ProvisioningPlan additionalActions) {
        _additionalActions = additionalActions;
    }

    /**
     * The names of any roles, excluding the primary role on the item, which
     * are being remediated. This might be required or permitted roles which are
     * to be removed, or a required role which is being provisioned.
     * 
     * @return non-null list of role names
     */
    public List<String> getAdditionalRemediations(){

        if (_additionalActions == null || _additionalActions.getAccountRequests() == null ||
                _additionalActions.getAccountRequests().isEmpty())
            return null;

        List<String> roles = new ArrayList<String>();
        for (ProvisioningPlan.AccountRequest acccountReq : _additionalActions.getAccountRequests()){
            if (acccountReq.getAttributeRequests() != null){
                for (ProvisioningPlan.AttributeRequest req : acccountReq.getAttributeRequests()){
                    String role = (String)req.getValue();
                    roles.add(role);
                }
            }
        }
        return roles;
    }

    public boolean hasFilteredRemediations() {
        return _additionalActions != null && !Util.isEmpty(_additionalActions.getFiltered());
    }

    @XMLProperty(mode= SerializationMode.REFERENCE_LIST)
    public List<CertificationAction> getParentActions() {
        return _parentActions;
    }

    public void setParentActions(List<CertificationAction> parentActions) {
        this._parentActions = parentActions;
    }

    public void addParentAction(CertificationAction newAction){
        if (this._parentActions == null)
            this._parentActions = new ArrayList<CertificationAction>();
        this._parentActions.add(newAction);
    }

    public void removeParentAction(CertificationAction action){
        if (this._parentActions == null)
            return;
        this._parentActions.remove(action);
    }

    public List<CertificationAction> getChildActions() {
        return _childActions;
    }

    @XMLProperty(mode= SerializationMode.REFERENCE_LIST)
    public void setChildActions(List<CertificationAction> requiredActions) {
        this._childActions = requiredActions;
    }

    public void addChildAction(CertificationAction newAction){
        if (this._childActions == null)
            this._childActions = new ArrayList<CertificationAction>();
        this._childActions.add(newAction);
    }

    public void removeChildAction(CertificationAction action){
        if (this._childActions == null)
            return;
        this._childActions.remove(action);
    }

    public CertificationAction getSourceAction() {
        return _sourceAction;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public void setSourceAction(CertificationAction sourceAction) {
        this._sourceAction = sourceAction;
    }

    public boolean isAutoDecision() { return this._autoDecision; }

    public void setAutoDecision(boolean autoDecision) { this._autoDecision = autoDecision; }

    //////////////////////////////////////////////////////////////////////
    //
    // Methods to modify action
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The decision is being changed, so clear any information that is already
     * set. The new information will be set by the action method.
     */
    protected void clearData() throws GeneralException {
        super.clearData();
        _mitigationExpiration = null;
        _remediationAction = null;
        _remediationDetails = null;
        _bulkCertified = false;
        _revokeAccount = false;
        remediationKickedOff = false;
        
        // TODO: reset reviewed?
    }

    /**
     * Mark this action as reviewed if it is being operated on by the
     * certification owner. This will make sure that decisions made by the
     * certification owner and then delegated via identity delegation do not
     * show up as requiring review.
     * 
     * @param  who       The identity making the action decision.
     * @param  workItem  The work item ID that the decision is being made in.
     *                   This is null if the decision is being made from the
     *                   certification.
     */
    private void reviewIfOwner(Identity who, String workItem) {
        if (null == workItem) {
            setReviewed(true);
        }
    }

    /**
     * Make a decision on this action. This is called by other action methods,
     * which will need to store any additional information they collection.
     * The fields being passed in here are the most common.
     */
    private void decide(Status status, String certificationId, Identity who,
                        String workItem, String comments)
        throws GeneralException {

        decide(status, certificationId, who, workItem, comments, false);
    }

    /**
     * This version of decide allows a custom autoDecision to be set on the action.
     */
    private void decide(Status status, String certificationId, Identity who,
                        String workItem, String comments, boolean isAutoDecision)
        throws GeneralException {

        clearData();
        reviewIfOwner(who, workItem);
        saveContext(who, workItem);
        _status = status;
        _comments = comments;
        _autoDecision = isAutoDecision;

        // Store the date when the decision is made.
        _decisionDate = new Date();
        _decisionCertificationId = certificationId;
    }
    
    /**
     * Approve.
     * 
     * @param  certId    The ID of the certification in which the decision is being made.
     * @param  who       The actor making the approval request.
     * @param  workItem  The work item ID in which the request is being made.
     * @param  comments  Optional comments for the approval                  
     */
    public void approve(String certId, Identity who, String workItem, String comments)
        throws GeneralException {

        decide(Status.Approved, certId, who, workItem, comments);
    }

    /**
     * Approve an item with some additional actions to perform. This is
     * currently being used to provision missing required roles, but could
     * be more general.
     */
    public void approve(String certId, Identity who, String workItem,
                        ProvisioningPlan additionalActions,
                        String additionalActionOwner, String additionalActionDesc,
                        String additionalActionComments)
        throws GeneralException {

        approve(certId, who, workItem, additionalActionComments);
        _additionalActions = additionalActions;
        _ownerName = additionalActionOwner;
        _description = additionalActionDesc;        
    }

    /**
     * Version of approve that allows for a custom autoDecision flag.
     */
    public void approve(String certId, Identity who, String workItem, String comments, boolean isAutoDecision)
        throws GeneralException {

        decide(Status.Approved, certId, who, workItem, comments, isAutoDecision);
    }

    /**
     * Acknowledged this action.
     * 
     * @param  certId    The ID of the certification in which the decision is being made.
     * @param  who       The actor making the decision.
     * @param  workItem  The work item ID in which the request is being made.
     * @param  comments  The comments for the acknowledgment.
     */
    public void acknowledge(String certId, Identity who, String workItem, String comments)
        throws GeneralException {

        decide(Status.Acknowledged, certId, who, workItem, comments);
    }

    /**
     * Cleared this action.
     * 
     * @param  certId    The ID of the certification in which the decision is being made.
     * @param  who       The actor making the decision.
     * @param  workItem  The work item ID in which the request is being made.
     * @param  comments  The comments for the decision.
     */
    public void clear(String certId, Identity who, String workItem, String comments)
        throws GeneralException {

        decide(Status.Cleared, certId, who, workItem, comments);
    }

    /**
     * Mitigate.
     * 
     * @param  certId    The ID of the certification in which the decision is being made.
     * @param  who       The actor making the mitigation request.
     * @param  workItem  The work item ID in which the request is being made.
     * @param  mitigationExpiration  The date that the mitigation expires.
     * @param  comments  The comments for the mitigation request.
     */
    public void mitigate(String certId, Identity who, String workItem,
                         Date mitigationExpiration, String comments)
        throws GeneralException {

        decide(Status.Mitigated, certId, who, workItem, comments);
        _mitigationExpiration = mitigationExpiration;
    }

    /**
     * Remediate.
     * 
     * @param  certId       The ID of the certification in which the decision is being made.
     * @param  who          The actor making the remediation request.
     * @param  workItem     The work item ID in which the request is being made.
     * @param  action       The RemediationAction to execute for this remediation.
     * @param  ownerName    The name of the recipient for the remediation request.
     * @param  description  The description of the remediation request.
     * @param  comments     The comments for the remediation request.
     * @param  details      The optional ProvisioningPlan with the remediation
     *                      details.  If not specified, this is calculated by
     *                      the certificationer when the remediation is flushed.
     * @param  additionalActions Any additional revokes to perform along with this remediation
     */
    public void remediate(String certId, Identity who, String workItem, RemediationAction action,
                          String ownerName, String description, String comments,
                          ProvisioningPlan details, ProvisioningPlan additionalActions)
        throws GeneralException {

        decide(Status.Remediated, certId, who, workItem, comments);
        _remediationAction = action;
        // The owner name provided here is the backup remediator.  When the 
        // actual remediation takes place, we will attempt to determine the
        // real remediator.  If that attempt fails, we fall back on this owner
        _ownerName = ownerName;
        _description = description;
        _remediationDetails = details;
        _additionalActions = additionalActions;

        // if the user has tacked on some additional revokes treat this as a bulk certification
        if (additionalActions != null && additionalActions.getAccountRequests() != null  &&
                !additionalActions.getAccountRequests().isEmpty()){
            _bulkCertified = true;
        }
    }

    /**
     * Special remediation request to revoke an account.
     * 
     * @param  certId       The ID of the certification in which the decision is being made.
     * @param  who          The actor making the remediation request.
     * @param  workItem     The work item ID in which the request is being made.
     * @param  action       The RemediationAction to execute for this remediation.
     * @param  ownerName    The name of the recipient for the remediation request.
     * @param  description  The description of the remediation request.
     * @param  comments     The comments for the remediation request.
     */
    public void revokeAccount(String certId, Identity who, String workItem, RemediationAction action,
                              String ownerName, String description, String comments)
        throws GeneralException {

        this.remediate(certId, who, workItem, action, ownerName, description, comments, null, null);
        _revokeAccount = true;
    }

    /**
     * Bulk certify this action using the information in the given action.
     * 
     * @param  certId      The ID of the certification in which the decision is being made.
     * @param  who         The actor making the bulk approval request.
     * @param  workItem    The work item ID in which the request is being made.
     * @param  bulkAction  The bulk action that contains the status and
     *                     parameters to use to bulk certify this action.
     */
    public void bulkCertify(String certId, Identity who, String workItem,
                            CertificationAction bulkAction)
        throws GeneralException {

        switch (bulkAction.getStatus()) {
        case Approved:
            approve(certId, who, workItem, bulkAction.getComments());
            break;
        case Mitigated:
            mitigate(certId, who, workItem, bulkAction.getMitigationExpiration(),
                     bulkAction.getComments());
            break;
        case Remediated:
            if (bulkAction.isRevokeAccount()) {
                revokeAccount(certId, who, workItem, bulkAction.getRemediationAction(),
                              bulkAction.getOwnerName(), bulkAction.getDescription(),
                              bulkAction.getComments());
            }
            else {
                remediate(certId, who, workItem, bulkAction.getRemediationAction(),
                          bulkAction.getOwnerName(), bulkAction.getDescription(),
                          bulkAction.getComments(), null, null);
            }
            break;
        default:
            throw new GeneralException("Unknown type of bulk certification: " + bulkAction.getStatus());
        }

        _bulkCertified = true;
    }
    
    /**
     * Indicates if the action was an approval.
     *
     * This method is here for use in jsf pages so a string
     * comparison on the type name is not needed.
     *
     * @return True if the action was an approval
     */
    public boolean isApproved(){
        return CertificationAction.Status.Approved.equals(this.getStatus());
    }

    /**
     * Indicates if the action was an mitigation.
     *
     * This method is here for use in jsf pages so string
     * comparison on the type name is not needed.
     *
     * @return True if the action was an mitigation
     */
    public boolean isMitigation(){
        return CertificationAction.Status.Mitigated.equals(this.getStatus());
    }

    /**
     * Indicates if the action was an delegation.
     *
     * This method is here for use in jsf pages so string
     * comparison on the type name is not needed.
     *
     * @return True if the action was an delegation
     */
    public boolean isDelegation(){
        return CertificationAction.Status.Delegated.equals(this.getStatus());
    }

    /**
     * Indicates if the action was an remediation.
     *
     * This method is here for use in jsf pages so string
     * comparison on the type name is not needed.
     *
     * @return True if the action was an remediation
     */
    public boolean isRemediation(){
        return CertificationAction.Status.Remediated.equals(this.getStatus());
    }

    /**
     * Indicates if the action was an acknowledgment.
     *
     * This method is here for use in jsf pages so string
     * comparison on the type name is not needed.
     *
     * @return True if the action was an acknowledgment
     */
    public boolean isAcknowledgment(){
        return CertificationAction.Status.Acknowledged.equals(this.getStatus());
    }
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // Deprecated action methods
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * @deprecated Use {@link #approve(String, Identity, String, String)}
     */
    @Deprecated
    public void approve(Identity who, String workItem, String comments) throws GeneralException {
        approve(null, who, workItem, comments);
    }

    /**
     * @deprecated Use {@link #approve(String, Identity, String, String)}
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public void approve(Identity who, String workItem) throws GeneralException {
        approve(who, workItem, null);
    }

    /**
     * @deprecated Use {@link #approve(String, Identity, String, ProvisioningPlan, String, String, String)}
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public void approve(Identity who, String workItem, ProvisioningPlan additionalActions,
                        String additionalActionOwner, String additionalActionDesc,
                        String additionalActionComments)
        throws GeneralException {

        approve(who, workItem);
        _additionalActions = additionalActions;
        _ownerName = additionalActionOwner;
        _comments = additionalActionComments;
        _description = additionalActionDesc;        
    }

    /**
     * @deprecated Use {@link #acknowledge(String, Identity, String, String)}
     */
    @Deprecated
    public void acknowledge(Identity who, String workItem, String comments) throws GeneralException {
        acknowledge(null, who, workItem, comments);
    }

    /**
     * @deprecated Use {@link #mitigate(String, Identity, String, Date, String)}
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public void mitigate(Identity who, String workItem, Date mitigationExpiration, String comments)
        throws GeneralException {
        mitigate(null, workItem, mitigationExpiration, comments);
    }
    
    /**
     * @deprecated Use {@link #remediate(String, Identity, String, RemediationAction, String, String, String, ProvisioningPlan, ProvisioningPlan)}
     */
    @Deprecated
    public void remediate(Identity who, String workItem, RemediationAction action,
                          String ownerName, String description, String comments,
                          ProvisioningPlan details, ProvisioningPlan additionalActions)
        throws GeneralException {
        remediate(null, who, workItem, action, ownerName, description, comments, details, additionalActions);
    }
    
    /**
     * @deprecated Use {@link #revokeAccount(String, Identity, String, RemediationAction, String, String, String)}
     */
    @Deprecated
    public void revokeAccount(Identity who, String workItem, RemediationAction action,
                              String ownerName, String description, String comments)
        throws GeneralException {
        revokeAccount(null, who, workItem, action, ownerName, description, comments);
    }

    /**
     * @deprecated Use {@link #bulkCertify(String, Identity, String, CertificationAction)}
     */
    @Deprecated
    public void bulkCertify(Identity who, String workItem, CertificationAction bulkAction)
        throws GeneralException {
        bulkCertify(null, who, workItem, bulkAction);
    }
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // Object overrides
    //
    ////////////////////////////////////////////////////////////////////////////
    
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this);
        builder.append("id", _id);
        builder.append("name", _name);
        builder.append("status", (null != _status) ? _status.getDisplayName() : null);
        return builder.toString();
    }
}
