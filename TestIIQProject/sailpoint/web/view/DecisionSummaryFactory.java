package sailpoint.web.view;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.certification.CertificationActionDescriber;
import sailpoint.api.certification.RemediationAdvisor;
import sailpoint.api.certification.RemediationManager;
import sailpoint.object.*;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.InvalidParameterException;
import sailpoint.web.certification.ProvisioningPlanEditDTO;
import sailpoint.web.messages.MessageKeys;

import java.util.*;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class DecisionSummaryFactory {

    private SailPointContext context;
    private Locale locale;
    private TimeZone timezone;
    private Identity currentUser;
    private RemediationManager remediationMgr;
    private RemediationAdvisor remediationAdvisor;

    public DecisionSummaryFactory(SailPointContext context, Identity currentUser, Locale locale, TimeZone timezone) {
        this.context = context;
        this.locale = locale;
        this.timezone = timezone;
        this.currentUser = currentUser;
        this.remediationMgr = new RemediationManager(context);
        this.remediationAdvisor = new RemediationAdvisor(context);
    }

    public DecisionSummary getSummary(CertificationItem item) throws GeneralException{
        return new DecisionSummary(context, locale, timezone, item);
    }

    public DecisionSummary calculateBulkSummary(Certification.Type certType, CertificationAction.Status status)
            throws GeneralException {

        CertificationActionDescriber describer = new CertificationActionDescriber(status, context);

        DecisionSummary summary = new DecisionSummary();
        summary.addAssignmentOptions(null, Internationalizer.getMessage(MessageKeys.ASSIGN_MANUAL, locale));
        summary.addAssignmentOptions(new IdentitySummary(currentUser), Internationalizer.getMessage(MessageKeys.ASSIGN_SELF, locale));
        summary.setDescription(describer.getBulkActionDescription(certType, status));

        return summary;
    }

    /**
     * Calculate decision summary for policy violation
     *
     * @param policyViolation policy violation to find summary for
     * @return DecisionSummary
     */
    public DecisionSummary calculateSummary(PolicyViolation policyViolation) throws GeneralException {
        if (policyViolation == null) {
            throw new InvalidParameterException("PolicyViolation required for calculating summary");
        }

        DecisionSummary decisionSummary = new DecisionSummary();
        RemediationManager.ProvisioningPlanSummary plan = this.remediationMgr.calculateRemediationDetails(policyViolation);

        Identity defaultRemediator = this.remediationAdvisor.calculateDefaultRemediator(policyViolation, plan);
        if (defaultRemediator != null) {
            decisionSummary.setDefaultRemediator(new IdentitySummary(defaultRemediator));
        }

        decisionSummary.setRemediationAction(plan.getAction());

        decisionSummary.setEnableOverrideDefaultRemediator(this.remediationAdvisor.isEnableOverrideDefaultRemediator());

        return decisionSummary;
    }

    public DecisionSummary calculateSummary(CertificationEntity entity, CertificationAction.Status status)
            throws GeneralException {

        CertificationActionDescriber describer = new CertificationActionDescriber(status, context);

        DecisionSummary summary = new DecisionSummary(entity);

        switch (status) {
            case Delegated:
                CertificationDelegation delegation = entity.getDelegation();
                if (delegation != null && delegation.isActive()) {
                    summary.setEntityDelegation(new DecisionSummary.DelegationSummary(delegation,
                            delegation.getOwner(context), true));
                } else {
                    summary.setDescription(describer.getDefaultDelegationDescription(entity));
                }
                break;
            case RevokeAccount:
            case Remediated:
                summary.setDescription(describer.getDefaultRemediationDescription(null, entity));
        }

        return summary;
    }

    public DecisionSummary calculateSummary(CertificationItem item, CertificationAction.Status status,
                                      List<Bundle> additionalRoles)
            throws GeneralException {

        CertificationActionDescriber describer = new CertificationActionDescriber(item, status, context);

        DecisionSummary summary = new DecisionSummary(context, locale, timezone, item);

        if (CertificationAction.Status.Delegated.equals(status)) {
            String defaultDesc = describer.getDefaultDelegationDescription(item.getParent());
            summary.setDescription(defaultDesc);

            // Editing an existing delegation, load the owner for the display name
            if (item.getDelegation() != null && item.getDelegation().getOwnerName() != null) {
                Identity owner = ObjectUtil.getIdentityOrWorkgroup(context, item.getDelegation().getOwnerName());
                if (owner != null)
                    summary.setOwner(new IdentitySummary(owner));
            }

        } else if (CertificationAction.Status.Remediated.equals(status) ||
                CertificationAction.Status.RevokeAccount.equals(status) ||
                CertificationAction.Status.Approved.equals(status)) {
            summary = calculateRemediationSummary(item, status, additionalRoles);
        }

        CertificationDelegation delegation = item.getDelegation() != null ? item.getDelegation() :
                item.getParent().getDelegation();
        if (delegation != null) {
            summary.setDelegation(new DecisionSummary.DelegationSummary(delegation, delegation.getOwner(context),
                    item.getParent().getDelegation() != null));
        }

        return summary;
    }

    private DecisionSummary calculateRemediationSummary(CertificationItem item, CertificationAction.Status status,
                                                  List<Bundle> additionalRoles)
            throws GeneralException {

        DecisionSummary summary = new DecisionSummary(context, locale, timezone, item);

        Configuration configuration = this.context.getConfiguration();
        summary.setUseManagedAttributesForRemediation(configuration.getBoolean(
                Configuration.USE_MANAGED_ATTRIBUTES_FOR_REMEDIATION, true));

        RemediationAdvisor advisor = new RemediationAdvisor(context);

        if (CertificationAction.Status.Approved.equals(status))
            additionalRoles = advisor.getMissingRequiredRoles(item);

        RemediationAdvisor.RemediationSummary advice =
                advisor.getRemediationSummary(status, item, additionalRoles);

        summary.addAssignmentOptions(null, Internationalizer.getMessage(MessageKeys.ASSIGN_MANUAL, locale));
        summary.addAssignmentOptions(new IdentitySummary(currentUser), Internationalizer.getMessage(MessageKeys.ASSIGN_SELF, locale));
        summary.setRemediationAction(advice.getRemediationAction());

        Identity defaultRemediator = advice.getDefaultRemediator();
        if (defaultRemediator != null){
            summary.setDefaultRemediator(new IdentitySummary(defaultRemediator));
        }
        
        summary.setEnableOverrideDefaultRemediator(advice.getEnableOverrideDefaultRemediator());

        ProvisioningPlanEditDTO remediationDetails = getPlanDTO(advice.getProvisioningPlan(), item);
        if (remediationDetails != null) {
            // If the plan has editable items, pass it back to the UI
            if (remediationDetails.hasEditableEntitlements()) {
                for (ProvisioningPlanEditDTO.LineItem lineItem : remediationDetails.getLineItems()) {
                    summary.addRemediationDetail(context, item.getIdentity(), lineItem, locale);
                }
            }

            Collection<String> provisioners = this.remediationMgr.getProvisioners(advice.getProvisioningPlan());
            if (provisioners != null && !provisioners.isEmpty()) {
                List<String> provisionerList = new ArrayList<String>();
                provisionerList.addAll(provisioners);
                summary.setProvisioners(provisionerList);
            }
        }

        if (summary.getDescription() == null) {
            CertificationActionDescriber describer = new CertificationActionDescriber(status, context);
            String defaultDesc = describer.getDefaultRemediationDescription(remediationDetails, item.getParent());
            summary.setDescription(defaultDesc);
        }

        Identity owner = null;
        if (item.getAction() != null && item.getAction().getOwnerName() != null) {
            owner = ObjectUtil.getIdentityOrWorkgroup(context, item.getAction().getOwnerName());
        }

        boolean isExistingRemediation = item.getAction() != null && status.equals(item.getAction().getStatus());
        if (!isExistingRemediation || owner == null) {
            if (defaultRemediator != null && summary.getOwner() == null)
                summary.setOwner(new IdentitySummary(defaultRemediator));
        } else {
            summary.setOwner(new IdentitySummary(owner));
        }

        if (configuration.getAttributes().getBoolean(Configuration.SHOW_CERTIFICATION_ROLE_REVOCATION_DETAILS, false)) {
            summary.setRequiredOrPermittedRoles(advisor.getRequiredOrPermittedRolesDisplayNames(item));
        }

        return summary;
    }

    private static ProvisioningPlanEditDTO getPlanDTO(ProvisioningPlan remediationPlan, CertificationItem item) {
        ProvisioningPlanEditDTO dto = null;
        if (remediationPlan != null) {

            boolean isExistingRemediation = item.getAction() != null &&
                    CertificationAction.Status.Remediated.equals(item.getAction().getStatus());

            // We need to know which schema this is, so that we can make the right decisions
            Certification.Type certType = item.getCertification().getType();
            String schemaName = Application.SCHEMA_ACCOUNT;
            if (certType.equals(Certification.Type.AccountGroupPermissions)) {
                schemaName = Application.SCHEMA_GROUP;
            }

            dto = new ProvisioningPlanEditDTO(remediationPlan, false,
                    isExistingRemediation, schemaName);
        }

        return dto;
    }
}
