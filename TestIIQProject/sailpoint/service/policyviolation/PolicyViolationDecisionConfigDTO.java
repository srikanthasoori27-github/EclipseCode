/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.policyviolation;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import sailpoint.object.Configuration;
import sailpoint.object.Duration;
import sailpoint.object.PolicyViolation;
import sailpoint.object.SPRight;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.web.Authorizer;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.view.certification.CertificationDecisionStatus;

/**
 * DTO to hold configuration for policy violation decisions
 */
public class PolicyViolationDecisionConfigDTO {

    public static final String STATUS_CERTIFIED = "Certified";

    /**
     * Flag to indicate user is allowed to edit the mitigation expiration date
     */
    private boolean allowMitigationExpirationEditing;

    /**
     * Default mitigation expiration date
     */
    private Date defaultMitigationExpirationDate;

    /**
     * If true, comments are required for mitigations
     */
    private boolean requireMitigationComments;

    /**
     * If true, comments are required for remediation
     */
    private boolean requireRemediationComments;

    /**
     * If true, user can choose different default remediator
     */
    private boolean enableOverrideDefaultRemediator;

    /**
     * List of bulk decisions that are allowed;
     */
    private List<CertificationDecisionStatus.ActionStatus> availableBulkDecisions;

    /**
     * Constructor
     * @param userContext UserContext
     */
    public PolicyViolationDecisionConfigDTO(UserContext userContext) throws GeneralException {
        if (userContext == null) {
            throw new InvalidParameterException("userContext");
        }
        Configuration configuration = userContext.getContext().getConfiguration();

        this.allowMitigationExpirationEditing =
                configuration.getBoolean(Configuration.CERTIFICATION_MITIGATION_POPUP_ENABLED, true);
        this.defaultMitigationExpirationDate = getConfiguredExpirationDate(configuration);

        // Always require comments!
        this.requireMitigationComments = true;
        this.requireRemediationComments = configuration.getBoolean(Configuration.REQUIRE_REMEDIATION_COMMENTS);

        this.availableBulkDecisions = setupAvailableBulkDecisions(configuration, userContext);

        this.enableOverrideDefaultRemediator = configuration.getBoolean(Configuration.ENABLE_OVERRIDE_VIOLATION_DEFAULT_REMEDIATOR);
    }

    private List<CertificationDecisionStatus.ActionStatus> setupAvailableBulkDecisions(Configuration configuration, UserContext userContext) {
        List<CertificationDecisionStatus.ActionStatus> decisions = new ArrayList<>();
        // Always allow bulk mitigation
        decisions.add(new CertificationDecisionStatus.ActionStatus(PolicyViolation.Status.Mitigated.name(), PolicyViolation.Status.Mitigated.getMessageKey(), MessageKeys.UI_POLICY_VIOLATION_BULK_DECISION_ALLOW));

        // Allow delegation if globally configured
        if (configuration.getBoolean(Configuration.CERTIFICATION_ITEM_DELEGATION_ENABLED, false)) {
            decisions.add(new CertificationDecisionStatus.ActionStatus(PolicyViolation.Status.Delegated.name(), PolicyViolation.Status.Delegated.getMessageKey(), MessageKeys.UI_POLICY_VIOLATION_BULK_DECISION_DELEGATE));
        }

        // Allow certification if user is system admin or has full access to schedule certifications
        if (Authorizer.hasAccess(userContext.getLoggedInUserCapabilities(), userContext.getLoggedInUserRights(), SPRight.FullAccessCertificationSchedule)) {
            decisions.add(new CertificationDecisionStatus.ActionStatus(STATUS_CERTIFIED, MessageKeys.UI_POLICY_VIOLATION_BULK_DECISION_CERTIFIED, MessageKeys.UI_POLICY_VIOLATION_BULK_DECISION_CERTIFY));
        }

        return decisions;
    }

    private Date getConfiguredExpirationDate(Configuration configuration) {
        Duration duration = (Duration)configuration.get(Configuration.CERTIFICATION_MITIGATION_DURATION);
        if (duration == null) {
            Long amount = configuration.getLong(Configuration.CERTIFICATION_MITIGATION_DURATION_AMOUNT);
            String scaleString = configuration.getString(Configuration.CERTIFICATION_MITIGATION_DURATION_SCALE);
            Duration.Scale scale = scaleString != null ? Duration.Scale.valueOf(scaleString) : null;
            duration = new Duration(amount, scale);
        }

        return duration.addTo(new Date());
    }

    /**
     * @return Flag to indicate user is allowed to edit the mitigation expiration date
     */
    public boolean isAllowMitigationExpirationEditing() {
        return allowMitigationExpirationEditing;
    }

    /**
     * @return Default mitigation expiration date
     */
    public Date getDefaultMitigationExpirationDate() {
        return defaultMitigationExpirationDate;
    }

    /**
     * @return If true, comments are required for mitigations
     */
    public boolean isRequireMitigationComments() {
        return requireMitigationComments;
    }

    public void setRequireMitigationComments(boolean requireMitigationComments) {
        this.requireMitigationComments = requireMitigationComments;
    }

    /**
     * @return If true, comments are required for remediations
     */
    public boolean isRequireRemediationComments() {
        return requireRemediationComments;
    }

    public void setRequireRemediationComments(boolean requireRemediationComments) {
        this.requireRemediationComments = requireRemediationComments;
    }

    public boolean isEnableOverrideDefaultRemediator() {
        return enableOverrideDefaultRemediator;
    }

    public List<CertificationDecisionStatus.ActionStatus> getAvailableBulkDecisions() {
        return availableBulkDecisions;
    }
}
