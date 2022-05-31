/*
 * (c) Copyright 2017. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.policyviolation;

import java.util.List;

import sailpoint.api.Localizer;
import sailpoint.api.SailPointContext;
import sailpoint.api.ViolationDetailer;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.PolicyViolation;
import sailpoint.service.certification.RemediationAdvice;
import sailpoint.service.certification.RemediationAdviceResult;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.certification.PolicyTreeNode;
import sailpoint.web.certification.PolicyViolationAdvisor;
import sailpoint.web.view.DecisionSummary;
import sailpoint.web.view.DecisionSummaryFactory;

/**
 * Service for getting policy violation related data
 */
public class PolicyViolationService {

    private SailPointContext context;
    private UserContext userContext;
    private PolicyViolationAdvisor advisor;
    private RemediationAdvice remediationAdvice;
    private Localizer localizer;

    /**
     *
     * @param userContext User context
     */
    public PolicyViolationService(UserContext userContext) throws GeneralException {
        this.userContext = userContext;
        this.context = userContext.getContext();
        this.remediationAdvice = new RemediationAdvice();
        this.localizer = new Localizer(this.context);
    }

    /**
     * Get the violation remediation advice data.
     *
     * @param policyViolation Policy violation to find remediation advice for
     * @return RemediationAdviceResult the remediation result
     * @throws GeneralException
     */
    public RemediationAdviceResult getViolationRemediationAdvice(PolicyViolation policyViolation) throws GeneralException {
        if (policyViolation == null) {
            throw new InvalidParameterException("Policy violation required");
        }

        RemediationAdviceResult result = new RemediationAdviceResult();

        ViolationDetailer violationDetailer = new ViolationDetailer(this.context, policyViolation,
                this.userContext.getLocale(), this.userContext.getUserTimeZone());

        advisor = new PolicyViolationAdvisor(this.context, policyViolation, this.userContext.getLocale());

        remediationAdvice.setViolationConstraint(violationDetailer.getConstraint());
        remediationAdvice.setViolationSummary(violationDetailer.getSummary());
        remediationAdvice.setRemediationAdvice(violationDetailer.getRemediationAdvice());

        // Since we are in the PV view, grab the setting from the global settings.
        boolean includeClassifications = Configuration.getSystemConfig().getBoolean(Configuration.CERTIFICATION_INCLUDE_CLASSIFICATIONS);

        // Set the entitlements if this is an entitlement SoD violation.
        remediationAdvice.setEntitlementsToRemediate(advisor.getEntitlementViolations(null, includeClassifications));

        List<Bundle> rightRoles = advisor.getRightBusinessRoles();
        this.addRoles(rightRoles, remediationAdvice, false, includeClassifications);

        List<Bundle> leftRoles = advisor.getLeftBusinessRoles();
        this.addRoles(leftRoles, remediationAdvice, true, includeClassifications);

        if (!remediationAdvice.requiresRemediationInput()) {
            result.setSummary(this.getRemediationSummary(null, null, policyViolation));
        }

        result.setAdvice(remediationAdvice);
        return result;
    }

    /**
     * Get the remediation summary data
     *
     * @param revokedRoles roles to revoke
     * @param selectedViolationEntitlements entitlements to revoke
     * @return DecisionSummary
     * @throws GeneralException
     */
    public DecisionSummary getRemediationSummary(List<String> revokedRoles,
                                                 List<PolicyTreeNode> selectedViolationEntitlements,
                                                 PolicyViolation policyViolation) throws GeneralException {
        if (policyViolation == null) {
            throw new InvalidParameterException("Policy violation required");
        }

        if (!Util.isEmpty(revokedRoles)) {
            policyViolation.setBundleNamesMarkedForRemediation(revokedRoles);
        }

        if (!Util.isEmpty(selectedViolationEntitlements)) {
            policyViolation.setEntitlementsToRemediate(selectedViolationEntitlements);
        }

        DecisionSummaryFactory decisionSummaryFactory = new DecisionSummaryFactory(this.context,
                this.userContext.getLoggedInUser(), this.userContext.getLocale(), this.userContext.getUserTimeZone());

        return decisionSummaryFactory.calculateSummary(policyViolation);
    }

    /**
     * Helper method to add roles to remediation advice
     */
    private void addRoles(List<Bundle> roles, RemediationAdvice remediationAdvice, boolean leftRoles,
                          boolean includeClassifications) throws GeneralException {
        for (Bundle role : Util.iterate(roles)) {
            String roleDesc = localizer.getLocalizedValue(role, Localizer.ATTR_DESCRIPTION, this.userContext.getLocale());

            List<String> classificationDisplayNames = includeClassifications ? role.getClassificationDisplayNames() : null;
            if (leftRoles) {
                remediationAdvice.addLeftRole(role.getId(), role.getName(), role.getDisplayableName(), roleDesc, advisor.isBusinessRoleSelected(role),
                                              classificationDisplayNames);
            }
            else {
                remediationAdvice.addRightRole(role.getId(), role.getName(), role.getDisplayableName(), roleDesc, advisor.isBusinessRoleSelected(role),
                                               classificationDisplayNames);
            }
        }
    }
}
