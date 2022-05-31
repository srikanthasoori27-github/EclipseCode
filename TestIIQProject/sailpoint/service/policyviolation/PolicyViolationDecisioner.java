/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.policyviolation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.ViolationDetailer;
import sailpoint.api.certification.PolicyViolationCertificationManager;
import sailpoint.api.certification.RemediationAdvisor;
import sailpoint.api.certification.RemediationManager;
import sailpoint.api.certification.SelfCertificationChecker;
import sailpoint.authorization.PolicyViolationAuthorizer;
import sailpoint.integration.RequestResult;
import sailpoint.object.CertificationAction;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityHistoryItem;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.service.SelectionModel;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.certification.PolicyTreeNode;
import sailpoint.web.certification.PolicyViolationJsonUtil;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.view.certification.CertificationDecisionStatus;

/**
 * Decisioner class to handle decisions on PolicyViolation objects.
 */
public class PolicyViolationDecisioner {

    private static final Log log = LogFactory.getLog(PolicyViolationDecisioner.class);

    /**
     * Statuses for the RequestResult to be returned after saving the decisions
     */
    public static final String SUCCESS = "Success";
    public static final String WARNING = "Warning";
    public static final String ERROR = "Error";

    /**
     * Key for count of invalid items in the RequestResult attributes map.
     */
    public static final String INVALID_ITEM_COUNT = "invalidItemCount";

    /**
     * SailPoint Context
     */
    private SailPointContext context;

    /** 
     * The identity who is performing the action
     */
    private Identity decider;

    /**
     * UserContext
     */
    private UserContext userContext;

    /** 
     * List to keep track of errors
     */
    private List<String> errors;

    /** 
     * List to keep track of warnings
     */
    private List<String> warnings;

    /**
     * List of IDs of Policy Violations that were invalid, meaning the status in a decision was not allowed on the
     * violation
     */
    private List<String> invalidItemIds;

    private PolicyViolationCertificationManager violationMgr;
    private ListFilterService listFilterService;
    private PolicyViolationListService policyViolationListService;

    private PolicyViolationDecisionConfigDTO decisionConfig;

    /**
     * Constructor
     * @param userContext The UserContext
     * @throws GeneralException If userContext is null
     */
    public PolicyViolationDecisioner(UserContext userContext, PolicyViolationListServiceContext listServiceContext) throws GeneralException {

        if (userContext == null) {
            throw new InvalidParameterException("userContext");
        }

        this.context = userContext.getContext();
        this.decider = userContext.getLoggedInUser();
        this.userContext = userContext;

        this.warnings = new ArrayList<>();
        this.errors = new ArrayList<>();

        this.violationMgr = new PolicyViolationCertificationManager(this.context);
        this.listFilterService = new ListFilterService(this.context, this.userContext.getLocale(), new PolicyViolationListFilterContext());
        this.policyViolationListService = new PolicyViolationListService(userContext, listServiceContext, null);

        this.decisionConfig = new PolicyViolationDecisionConfigDTO(this.userContext);
    }

    /**
     * Perform actions on a list of PolicyViolationDecision objects 
     * @param decisions List of PolicyViolationDecision objects
     * @return DecisionResults containing the status of the decisions with any errors and/or warnings
     */
    public RequestResult decide(List<PolicyViolationDecision> decisions) throws GeneralException {

        for (PolicyViolationDecision decision : Util.iterate(decisions)) {

            List<String> policyViolationIds = this.getObjectIds(decision);
            PolicyViolation.Status newStatus;
            try {
                newStatus = Enum.valueOf(PolicyViolation.Status.class, decision.getStatus());
            } catch (Exception ex) {
                throw new GeneralException("Invalid status " + decision.getStatus());
            }

            // Bulk Remediations are not supported.
            if (Util.size(policyViolationIds) > 1 && PolicyViolation.Status.Remediated.equals(newStatus)) {
                addError(MessageKeys.UI_BULK_DECISION_REMEDIATION_ERROR);
                continue;
            }

            int decisionCount = 0;
            for (String id : Util.iterate(policyViolationIds)) {

                PolicyViolation policyViolation = this.context.getObjectById(PolicyViolation.class, id);
                if (policyViolation == null) {
                    throw new ObjectNotFoundException(PolicyViolation.class, id);
                }

                // Authorize user against this violation
                PolicyViolationAuthorizer authorizer = new PolicyViolationAuthorizer(policyViolation);
                if (!authorizer.isAuthorizedUser(this.userContext)) {
                    addError(new Message(MessageKeys.UI_UNAUTHORIZED_USER_POLICY_VIOLATION, policyViolation.getName()));
                    continue;
                }

                // can't make a decision on remediated violation items since they are final
                if (Util.nullSafeEq(policyViolation.getStatus(), PolicyViolation.Status.Remediated)) {
                    addDecisionError(policyViolation);
                    continue;
                }

                // Check that the status is allowed on this violation
                if (!isStatusAllowed(policyViolation, decision.getStatus())) {
                    addInvalidItem(policyViolation);
                    continue;
                }

                switch (newStatus) {
                    case Mitigated:
                        mitigate(policyViolation, decision);
                        ++decisionCount;
                        break;
                    case Delegated:
                        delegate(policyViolation, decision);
                        ++decisionCount;
                        break;
                    case Remediated:
                        remediate(policyViolation, decision);
                        ++decisionCount;
                        break;
                    default:
                        addDecisionError(policyViolation);
                        break;
                }

                if (decisionCount % 20 == 0){
                    this.commitAndDecache();
                }
            }
        }

        // commit the decision
        this.context.commitTransaction();

        // Create the result, including the invalidItemId count if any exist
        RequestResult result = new RequestResult(getSummaryStatus(), null, this.warnings, this.errors);
        if (!Util.isEmpty(this.invalidItemIds)) {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put(INVALID_ITEM_COUNT, Util.size(this.invalidItemIds));
            result.setAttributes(attributes);
        }

        return result;
    }

    /**
     * Helper method to get the targeted identity ids & names for a list of policy violations
     *
     * @param policyViolationIds List<String> list of policy violation ids
     * @return Map<String, List<String>> map of identities; key "ids" for identity.id & key "names" for identity.name
     * @throws GeneralException
     */
    public Map<String, List<String>> getPolicyViolationIdentities(List<String> policyViolationIds) throws GeneralException {
        Set<String> availableIdentityIds = new HashSet<>();
        Set<String> availableIdentityNames = new HashSet<>();

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.in("id", policyViolationIds));
        ops.setDistinct(true);

        Iterator<Object[]> identityIdIterator = this.context.search(PolicyViolation.class, ops,
                Arrays.asList("identity.id", "identity.name"));
        while (identityIdIterator.hasNext()) {
            Object[] objects = identityIdIterator.next();
            if (objects != null && objects.length > 0 && objects[0] != null) {
                availableIdentityIds.add((String)objects[0]);
                availableIdentityNames.add((String)objects[1]);
            }
        }

        Map<String, List<String>> availableIdentities = new HashMap<>();
        availableIdentities.put("ids", new ArrayList<>(availableIdentityIds));
        availableIdentities.put("names", new ArrayList<>(availableIdentityNames));
        return availableIdentities;
    }

    /**
     * Generates a remediation.
     * Make sure roles/entitlements are selected if required.
     * Verify assignee is set for workitem if required.
     * At that point a decision is made as to whether we can auto-remediate this violation.
     * Once we have either an assignee or a action for auto-remediation we create the remediation work item.
     */
    private void remediate(PolicyViolation violation, PolicyViolationDecision decision) throws GeneralException {
        // make sure roles are selected if required
        if (this.isRequirePolicyBusinessRoles(violation)) {
            if (Util.isEmpty(decision.getRevokedRoles())) {
                addError(MessageKeys.UI_REMEDIATION_NO_ROLES_SELECTED);
                return;
            }
            else {
                violation.setBundleNamesMarkedForRemediation(decision.getRevokedRoles());
            }
        }
        // check if entitlements were selected for correction
        //TODO: EffectiveEntitlements?
        else if (this.isEntitlementSodViolation(violation)) {
            if (Util.isNullOrEmpty(decision.getSelectedViolationEntitlements())) {
                addError(new Message(MessageKeys.UI_REMEDIATION_NO_ENTITLEMENTS_SELECTED));
                return;
            }
            else {
                List<PolicyTreeNode> response = PolicyViolationJsonUtil.decodeSelectedEntitlementsJson(decision.getSelectedViolationEntitlements());
                violation.setEntitlementsToRemediate(response);
            }
        }

        //TODO: Needs to be true for Effective policies
        boolean requireRemediationWorkItem = (CertificationAction.RemediationAction.OpenWorkItem.equals(violationMgr.getRemediationManager().calculateRemediationAction(violation)) ||
                CertificationAction.RemediationAction.NoActionRequired.equals(violationMgr.getRemediationManager().calculateRemediationAction(violation)));

        Identity recipient = ObjectUtil.getIdentityOrWorkgroup(context, decision.getRecipient());

        // make sure assignee exists if remediation work item is needed
        //If there is no recipient set on the decision lookup the default remediator.
        if (requireRemediationWorkItem) {
            RemediationManager.ProvisioningPlanSummary plan = violationMgr.getRemediationManager().calculateRemediationDetails(violation);
            RemediationAdvisor remediationAdvisor = new RemediationAdvisor(context);
            Identity defaultRemediator = remediationAdvisor.calculateDefaultRemediator(violation, plan);

            // if no decision recipient was selected and there is no default remediator
            if (recipient == null) {
                if (defaultRemediator == null) {
                    addError(MessageKeys.UI_REMEDIATION_RECIPIENT_REQUIRED_ERROR);
                    return;
                }
                recipient = defaultRemediator;
            }
            else if (!validateRemediationRecipient(recipient, defaultRemediator)) {
                // if there was a decision recipient make sure its valid
                addError(new Message(MessageKeys.UI_REMEDIATION_INVALID_RECIPIENT));
                return;
            }
        }

        this.violationMgr.getRemediationManager().setExpandRoleAssignments(true);
        this.violationMgr.getRemediationManager().setPreserveDetectedRoles(false);

        // its possible for the recipient to be null so deal with that here.
        String assigneeName = "";
        if (recipient != null) {
            assigneeName = recipient.getName();
        }

        // save the bundles marked for remediation and send the remediation request
        this.violationMgr.remediate(violation, decider, assigneeName, getRemediationDescription(violation), decision.getComments());

        // reset flags
        this.violationMgr.getRemediationManager().setExpandRoleAssignments(false);
        this.violationMgr.getRemediationManager().setPreserveDetectedRoles(true);
    }

    /**
     * Check to make sure mitigation expiration date is valid. Mitigation comments are required.
     *
     * @param violation the policy violation to be mitigated
     * @param decision the decision data object
     * @throws GeneralException
     */
    private void mitigate(PolicyViolation violation, PolicyViolationDecision decision) throws GeneralException {
        try {
            Date expirationDate = decision.getMitigationExpirationDate() != null ?
                    new Date(decision.getMitigationExpirationDate()) : new Date();

            if (!this.validateMitigationExpirationDate(violation, expirationDate)) {
                addError(MessageKeys.UI_MITIGATION_EXPIRATION_DATE_INVALID);
            }
            else if (!this.validateMitigationComments(decision.getComments())) {
                addError(MessageKeys.UI_MITIGATION_INVALID_COMMENT);
            }
            else {
                this.violationMgr.mitigate(violation, this.decider, expirationDate, decision.getComments());
            }
        } catch (GeneralException exception) {
            if (log.isErrorEnabled()) {
                log.error("Unable to make decision on the policyViolation [" + violation.getDisplayableName() + "]: " + exception.getMessage(), exception);
            }
            addError(new Message(MessageKeys.UI_MITIGATION_DECISION_ERROR, violation.getName()));
        }
    }

    /**
     * Gets the current mitigation expiration date if there is an existing mitigation on the policy violation.
     * If the violation is not yet mitigated then the method returns 0.
     * We use the current expiration date for validation.
     * @param violation PolicyViolation to get the current mitigation expiration date from.
     * @return long value of the expiration date timestamp. Returns 0 if there was no prior mitigation.
     * @throws GeneralException
     */
    private long getCurrentMitigationExpiration(PolicyViolation violation) throws GeneralException {
        ViolationDetailer violationDetailer = new ViolationDetailer(this.context, violation,
                this.userContext.getLocale(), this.userContext.getUserTimeZone());
        long currentExpirationDate = 0;
        IdentityHistoryItem descItem = violationDetailer.getCurrentDecision();
        if (descItem != null) {
            CertificationAction desc = descItem.getAction();
            if (desc.isMitigation() && desc.getMitigationExpiration() != null) {
                currentExpirationDate = desc.getMitigationExpiration().getTime();
            }
        }
        return currentExpirationDate;
    }

    private void delegate(PolicyViolation violation, PolicyViolationDecision decision) throws GeneralException {

        Identity recipient = ObjectUtil.getIdentityOrWorkgroup(this.context, decision.getRecipient());

        // check for no recipient
        if (recipient == null) {
            addError(MessageKeys.UI_DELEGATION_RECIPIENT_REQUIRED_ERROR);
        }
        // check if the recipient is the same as the violation owner.
        else if (recipient.equals(violation.getOwner())) {
            addError(new Message(MessageKeys.UI_DELEGATION_RECIPIENT_ERROR, violation.getName()));
        }
        // check for self-certification
        else if (isSelfCertify(recipient, violation)) {
            addError(new Message(MessageKeys.UI_DELEGATION_SELF_CERTIFICATION_ERROR, violation.getName(), recipient.getDisplayableName()));
        }
        else { 
            try {
                String description = decision.getDescription();
                if (Util.isNullOrEmpty(description)) {
                    description = this.getDelegationDescription(violation);
                }
                this.violationMgr.delegate(violation, this.decider, recipient, description, decision.getComments());
            }
            catch (GeneralException exception) {
                if (log.isErrorEnabled()) {
                    log.error("Unable to make decision on the policyViolation [" + violation.getDisplayableName() + "]: " + exception.getMessage(), exception);
                }
                addError(new Message(MessageKeys.UI_DELEGATION_DECISION_ERROR, violation.getName()));
            }
        }
    }

    /**
     * Helper method to return the description to be used for delegation
     * @throws GeneralException 
     */
    private String getDelegationDescription(PolicyViolation policyViolation) throws GeneralException {

        if (policyViolation != null) {
            Message message = new Message(MessageKeys.CERTIFY_VIOLATION_DESC, policyViolation.getDisplayableName(), policyViolation.getIdentity().getDisplayableName());
            return message.getLocalizedMessage(this.userContext.getLocale(), this.userContext.getUserTimeZone());
        }
        return null;
    }

    /**
     * Helper method to add decision error message
     */
    private void addDecisionError(PolicyViolation policyViolation) {
        addError(new Message(MessageKeys.UI_POLICY_VIOLATION_DECISION_ERROR, policyViolation.getName()));
    }

    /**
     * Helper method to add an error message
     * @param messageKey The message key
     */
    private void addError(String messageKey) {
        addError(new Message(messageKey));
    }

    /**
     * Helper method to add an error message
     * @param message The message
     */
    private void addError(Message message) {
        this.errors.add(message.getLocalizedMessage(this.userContext.getLocale(), null));
    }

    /**
     * Helper method to add an invalid item
     * @param policyViolation The invalid policy violation
     */
    private void addInvalidItem(PolicyViolation policyViolation) {
        if (this.invalidItemIds == null) {
            this.invalidItemIds = new ArrayList<>();
        }

        this.invalidItemIds.add(policyViolation.getId());
    }

    /**
     * Check if this violation involve business roles
     */
    private boolean isRequirePolicyBusinessRoles(PolicyViolation violation) throws GeneralException {
        Policy policy = violation.getPolicy(this.context);
        return policy != null && policy.isType(Policy.TYPE_SOD);
    }

    /**
     * Check if this violation is an entitlement sod
     * TODO: Effective considered entitlementSOD as well?
     */
    private boolean isEntitlementSodViolation(PolicyViolation violation) throws GeneralException {
        Policy policy = violation.getPolicy(this.context);
        return policy != null && (policy.isType(Policy.TYPE_ENTITLEMENT_SOD)
                || policy.isType(Policy.TYPE_EFFECTIVE_ENTITLEMENT_SOD));
    }

    /**
     * Get the remediation description
     */
    private String getRemediationDescription(PolicyViolation violation) throws GeneralException {
        String idName = violation.getIdentity() != null ? violation.getIdentity().getFullName() :
                violation.getIdentity().getName();
        return new Message(MessageKeys.REMEDIATE_VIOLATION_WORKITEM_DESC, violation.getDisplayableName(), idName).getLocalizedMessage();
    }

    /**
     * If default remediator cannot be overridden make sure recipient is the default remediator
     *
     * @param recipient Identity remediation work item recipient
     * @param defaultRemediator Identity default remediator
     * @return boolean true if recipient is valid
     */
    private boolean validateRemediationRecipient(Identity recipient, Identity defaultRemediator) {
        if (recipient == null) {
            return false;
        }

        // if you can override the default remediator and then the recipient is valid
        if (decisionConfig.isEnableOverrideDefaultRemediator()) {
            return true;
        }
        // if you can not override the default remediator then check if the recipient is the default remediator
        else if (defaultRemediator != null) {
            return recipient.equals(defaultRemediator);
        }

        return false;
    }

    /**
     * Validate the mitigation expiration date.
     * If expiration date editing is allowed then check to make sure date is in the future.
     * If expiration date editing is NOT allowed then the date should be the default expiration date.
     * If an existing mitigation decision is being update the date is only checked if it has been modified.
     *
     * @param violation PolicyViolation to be mitigated
     * @param expirationDate Date the expiration date
     * @return boolean true if valid
     */
    private boolean validateMitigationExpirationDate(PolicyViolation violation, Date expirationDate) throws GeneralException {
        if (expirationDate == null) {
            return false;
        }

        //if the status is not already mitigated then we are creating a new mitigation.
        boolean isCreate = !MessageKeys.POLICY_VIOLATION_MITIGATED.equals(violation.getStatus().getMessageKey());
        boolean dateChanged = false;
        long currentExpiration = getCurrentMitigationExpiration(violation);
        if (!isCreate && currentExpiration != expirationDate.getTime()) {
            dateChanged = true;
        }

        // Don't allow the date to be changed if we have editing disabled. IIQMAG-1780
        if (dateChanged && !decisionConfig.isAllowMitigationExpirationEditing()) {
            return false;
        }

        boolean valid = true;
        // Expiration date should not be in the past
        // Only validate when the date is editable and has changed or it is the first mitigation
        if (decisionConfig.isAllowMitigationExpirationEditing() && (isCreate || dateChanged)) {
            valid = (new Date()).compareTo(expirationDate) < 0;
        } else if (isCreate) {
            int daysDifference = Util.getDaysDifference(expirationDate, decisionConfig.getDefaultMitigationExpirationDate());
            valid = (daysDifference == 0);
        }
        return valid;
    }

    /**
     * Validate mitigation comments.
     *
     * If mitigation comments are required but not provided then comments are invalid.
     *
     * @param comments String comments
     * @return boolean true if comments are required and provided
     */
    private boolean validateMitigationComments(String comments) {
        boolean valid = true;
        if (decisionConfig.isRequireMitigationComments() && Util.isNullOrEmpty(comments)) {
            valid = false;
        }
        return valid;
    }

    /**
     * Get a list of policy violation ids to make a decision for
     *
     * @param decision PolicyViolationDecision object
     * @return List of policy violation ids
     */
    public List<String> getObjectIds(PolicyViolationDecision decision) throws GeneralException {
        if (decision == null) {
            throw new InvalidParameterException("PolicyViolationDecision decision");
        }

        List<String> violationIds = null;
        SelectionModel selectionModel = decision.getSelectionModel();
        if (!selectionModel.isSelectAll()) {
            violationIds = selectionModel.getItemIds();
        } else {
            // Select all decision, build up some filters
            QueryOptions ops = new QueryOptions();

            // First add basic filters
            this.policyViolationListService.addBasicPolicyViolationFilters(ops);

            // Then add any filters inside the decision selection model
            if (!Util.isEmpty(selectionModel.getFilterValues())) {
                List<Filter> selectionModelFilters =
                        this.listFilterService.convertQueryParametersToFilters(selectionModel.getFilterValues(), true);
                for (Filter filter : selectionModelFilters) {
                    ops.add(filter);
                }
            }

            // Lastly add filter for any exclusions
            if (!Util.isEmpty(selectionModel.getItemIds())) {
                ops.add(Filter.not(Filter.in("id", selectionModel.getItemIds())));
            }

            Iterator<Object[]> idResults = this.context.search(PolicyViolation.class, ops, "id");
            while (idResults.hasNext()) {
                if (violationIds == null) {
                    violationIds = new ArrayList<>();
                }
                violationIds.add((String)idResults.next()[0]);
            }
        }

        if (violationIds == null) {
            violationIds = new ArrayList<>();
        }

        return violationIds;
    }

    /**
     * Check if the given status is allowed on the given policy violation
     * @param violation PolicyViolation
     * @param status String representation of PolicyViolationAction.Status
     * @return True if allowed, otherwise false
     * @throws GeneralException
     */
    private boolean isStatusAllowed(PolicyViolation violation, String status) throws GeneralException {
        List<CertificationDecisionStatus.ActionStatus> allowedActions = this.policyViolationListService.getPolicyViolationDecisionChoices(this.context, violation);
        for (CertificationDecisionStatus.ActionStatus actionStatus: Util.iterate(allowedActions)) {
            if (status.equals(actionStatus.getName())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get summary status based on errors, warnings.
     *
     * @return Status for the results
     */
    private String getSummaryStatus() {
        if (!Util.isEmpty(this.errors)) {
            return ERROR;
        }
        else if (!Util.isEmpty(this.warnings) || !Util.isEmpty(this.invalidItemIds)) {
            return WARNING;
        }
        return SUCCESS;
    }

    /**
     * Commit and decache the transaction
     */
    private void commitAndDecache() throws GeneralException {
        this.context.commitTransaction();
        this.context.decache();
        this.decider = ObjectUtil.reattach(this.context, this.decider);
    }

    /**
     * Check to see if the recipient is the targeted identity on the policy violation and if it results in self-certification
     * @param recipient Identity to check for
     * @param violation PolicyViolation to check for the targeted identity
     * @return true if the delegation will result in self-certification
     */
    private boolean isSelfCertify(Identity recipient, PolicyViolation violation) throws GeneralException {
        // recipient is same as the targeted identity on the violation and self-certify is not allowed per configuration
        return (recipient.equals(violation.getIdentity()) && !SelfCertificationChecker.isSelfCertifyAllowed(recipient, this.context));
    }
}
