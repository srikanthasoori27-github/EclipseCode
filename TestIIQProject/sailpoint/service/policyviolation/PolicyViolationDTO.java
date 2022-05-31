/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.policyviolation;

import java.util.List;

import sailpoint.api.ObjectUtil;
import sailpoint.api.ViolationDetailer;
import sailpoint.object.ApplicationActivity;
import sailpoint.object.CertificationAction;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityHistoryItem;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.object.WorkItem;
import sailpoint.policy.AccountPolicyExecutor;
import sailpoint.service.IdentitySummaryDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.certification.PolicyTreeNode;
import sailpoint.web.certification.PolicyViolationJsonUtil;
import sailpoint.web.view.certification.CertificationDecisionStatus;

/**
 * DTO to represent a PolicyViolation object.
 */
public class PolicyViolationDTO {

    /**
     * PolicyViolation id
     */
    private String id;

    /**
     * Identity details
     */
    private IdentitySummaryDTO identity;

    /**
     * Owner of the policy violation
     */
    private IdentitySummaryDTO owner;

    /**
     * The policy object
     */
    private PolicyDTO policy;

    /**
     * Policy violation rule - This is the constraint name
     */
    private String rule;

    /**
     * Violation status
     */
    private String status;

    /**
     * Description of the policy rule
     */
    private String ruleDescription;

    /**
     * Policy violation compensating control
     */
    private String compensatingControl;

    /**
     * Policy violation remediation advice
     */
    private String remediationAdvice;

    /**
     * Name of the policy violation application
     */
    private String applicationName;

    /**
     * Name of the policy violation account
     */
    private String accountName;

    /**
     * Policy score weight
     */
    private int scoreWeight;

    /**
     * SOD conflict
     */
    private String sodConflict;

    /**
     * Violation Summary
     */
    private String summary;

    /**
     * List of allowed actions for this policy violation
     */
    private List<CertificationDecisionStatus.ActionStatus> allowedActions;

    /**
     * Displayable name for violation
     */
    private String displayName;

    /**
     * Decision details
     */
    private BasePolicyViolationDecision decision;

    /**
     * Activity Details for policies of activity type
     */
    private ApplicationActivityDTO activity;

    /**
     * Is a violation editable? Violations that have been remediated are final and not editable.
     */
    private boolean editable = true;

    /**
     * Constructor
     *
     * @param policyViolation PolicyViolation object
     * @param userContext UserContext
     * @throws GeneralException
     */
    @SuppressWarnings("rawtypes")
    public PolicyViolationDTO(PolicyViolation policyViolation, UserContext userContext)
        throws GeneralException {

        this.id = policyViolation.getId();
        this.owner = policyViolation.getOwner() != null ? new IdentitySummaryDTO(policyViolation.getOwner()) : null;
        this.rule = policyViolation.getConstraintName();
        this.applicationName = (String)policyViolation.getArgument(AccountPolicyExecutor.VIOLATION_APP_NAME);
        this.accountName = Util.listToCsv((List)policyViolation.getArgument(AccountPolicyExecutor.VIOLATION_ACCOUNTS));

        ViolationDetailer violationDetailer = new ViolationDetailer(userContext.getContext(), policyViolation,
                userContext.getLocale(), userContext.getUserTimeZone());

        if (violationDetailer.getIdentityId() != null) {
            this.identity = new IdentitySummaryDTO(violationDetailer.getIdentityId(),
                    violationDetailer.getIdentityName(),
                    violationDetailer.getIdentityName());
            this.identity.setFirstName(violationDetailer.getIdentityFirstname());
            this.identity.setLastName(violationDetailer.getIdentityLastname());
        }

        this.compensatingControl = violationDetailer.getCompensatingControl();
        this.remediationAdvice = violationDetailer.getRemediationAdvice();
        if (violationDetailer.getConstraintWeight() != null) {
            this.scoreWeight = Integer.parseInt(violationDetailer.getConstraintWeight());
        }
        this.ruleDescription = violationDetailer.getConstraintDescription();
        this.status = violationDetailer.getStatus();

        this.sodConflict = violationDetailer.getSodConflict();

        this.summary = violationDetailer.getSummary();

        this.displayName = violationDetailer.getConstraint();

        this.policy = new PolicyDTO(violationDetailer.getPolicy(), userContext);
        this.decision = this.createBaseDecision(policyViolation, userContext);
        this.activity = this.createApplicationActivityDTO(violationDetailer.getActivity());

        // only remediated violations are not editable
        if (this.decision != null) {
            this.editable = !Util.nullSafeEq(this.decision.getStatus(), CertificationAction.Status.Remediated.name());
        }
    }

    public String getId() {
        return id;
    }

    public IdentitySummaryDTO getIdentity() {
        return identity;
    }

    public IdentitySummaryDTO getOwner() {
        return owner;
    }

    public PolicyDTO getPolicy() {
        return policy;
    }

    public String getRuleDescription() {
        return ruleDescription;
    }

    public String getCompensatingControl() {
        return compensatingControl;
    }

    public String getRemediationAdvice() {
        return remediationAdvice;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getAccountName() {
        return accountName;
    }

    public int getScoreWeight() {
        return scoreWeight;
    }

    public String getRule() {
        return rule;
    }

    public String getSodConflict() {
        return sodConflict;
    }

    public String getSummary() {
        return summary;
    }

    public String getStatus() {
        return status;
    }

    public List<CertificationDecisionStatus.ActionStatus> getAllowedActions() {
        return allowedActions;
    }

    public void setAllowedActions(List<CertificationDecisionStatus.ActionStatus> allowedActions) {
        this.allowedActions = allowedActions;
    }

    public BasePolicyViolationDecision getDecision() {
        return decision;
    }

    public void setDecision(BasePolicyViolationDecision decision) {
        this.decision = decision;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isEditable() {
        return editable;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }

    /**
     * Returns the currentWorkItem, if it exists for this violation.
     * @param violation PolicyViolation object to check for if workItem was created
     * @param userContext UserContext
     * @return Current workItem for the violation.
     * @throws GeneralException
     */
    private WorkItem getCurrentWorkItem(PolicyViolation violation, UserContext userContext) throws GeneralException {

        WorkItem currentWorkItem = null;

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("targetId", violation.getId()));
        List<WorkItem> items = userContext.getContext().getObjects(WorkItem.class, ops);
        if (items != null && !items.isEmpty()) {
            currentWorkItem = items.get(0);
        }
        return currentWorkItem;
    }

    /**
     * Return the decision made on the violation
     * @param violation Policy violation to get the decision for
     * @param userContext UserContext
     * @return IdentityHistoryItem Decision details for the violation
     * @throws GeneralException
     */
    private IdentityHistoryItem getCurrentDecision(PolicyViolation violation, UserContext userContext) throws GeneralException {
        ViolationDetailer violationDetailer = new ViolationDetailer(
            userContext.getContext(),
            violation,
            userContext.getLocale(),
            userContext.getUserTimeZone());
        return violationDetailer.getCurrentDecision();
    }

    /**
     * Return the decision details for the policy violation.
     * @param violation PolicyViolation to get the decision details for
     * @param userContext UserContext
     * @return BaseDecision object containing the decision details
     * @throws GeneralException
     */
    private BasePolicyViolationDecision createBaseDecision(PolicyViolation violation, UserContext userContext) throws GeneralException {

        BasePolicyViolationDecision baseDecision = null;

        if (PolicyViolation.Status.Delegated.equals(violation.getStatus())) {
            WorkItem workItem = this.getCurrentWorkItem(violation, userContext);
            if (workItem != null) {
                baseDecision = new BasePolicyViolationDecision();
                Identity owner = workItem.getOwner();
                if (owner != null) {
                    baseDecision.setRecipient(owner.getId());
                    baseDecision.setRecipientSummary(new IdentitySummaryDTO(owner.getId(), owner.getName(), owner.getDisplayName()));
                }
                baseDecision.setStatus(violation.getStatus().name());
                baseDecision.setComments(Util.listToCsv(workItem.getComments()));
                baseDecision.setDescription(workItem.getDescription());
                baseDecision.setActorDisplayName(workItem.getRequester() != null ? workItem.getRequester().getDisplayableName() : null);
                baseDecision.setCreated(workItem.getCreated());
            }
        } else if (!PolicyViolation.Status.Open.equals(violation.getStatus())){
            IdentityHistoryItem decision = this.getCurrentDecision(violation, userContext);
            CertificationAction action = (decision != null) ? decision.getAction() : null;

            if (action != null) {
                baseDecision = new BasePolicyViolationDecision();
                baseDecision.setStatus(action.getStatus().name());
                baseDecision.setComments(action.getComments());
                baseDecision.setDescription(action.getDescription());
                baseDecision.setActorDisplayName(action.getActorDisplayName());
                baseDecision.setCreated(action.getCreated());
                // Mitigated
                if (action.isMitigation() && action.getMitigationExpiration() != null) {
                    baseDecision.setMitigationExpirationDate(action.getMitigationExpiration().getTime());
                }
                // set remediation data
                else if (action.isRemediation()) {
                    if (Util.isNotNullOrEmpty(violation.getBundlesMarkedForRemediation())) {
                        baseDecision.setRevokedRoles(violation.getBundleNamesMarkedForRemediation());
                    }

                    List<PolicyTreeNode> entitlements = violation.getEntitlementsToRemediate();
                    if (!Util.isEmpty(entitlements)) {
                        baseDecision.setSelectedViolationEntitlements(
                                PolicyViolationJsonUtil.encodeEntitlementsPolicyTreeNodeList(entitlements));
                    }

                    String actionOwnerName = action.getOwnerName();

                    if (actionOwnerName != null) {
                        Identity actionOwner = ObjectUtil.getIdentityOrWorkgroup(userContext.getContext(), action.getOwnerName());
                        if (actionOwner != null) {
                            baseDecision.setRecipient(actionOwner.getId());
                            baseDecision.setRecipientSummary(new IdentitySummaryDTO(actionOwner));
                        }
                    }
                }
            }
        }

        return baseDecision;
    }

    /**
     * Create the ApplicationActivityDTO from the activity details
     * @param activity ApplicationActivity
     * @return ApplicationActivityDTO with the activity details
     * @throws GeneralException
     */
    private ApplicationActivityDTO createApplicationActivityDTO(ApplicationActivity activity) throws GeneralException {
        if (activity != null) {
            return new ApplicationActivityDTO(activity);
        }
        return null;
    }

    public ApplicationActivityDTO getActivity() {
        return activity;
    }

    public void setActivity(ApplicationActivityDTO activity) {
        this.activity = activity;
    }
}