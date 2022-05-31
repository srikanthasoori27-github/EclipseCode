/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.certification;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import sailpoint.api.Notary;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition.UserInterfaceInputType;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.Configuration;
import sailpoint.object.Duration;
import sailpoint.object.Filter;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;

/**
 * Class to hold certification configuration values used by the UI layer.
 */
public class CertificationConfigDTO {

    private List<CertificationAction.Status> statusesRequiringComments;
    private String esigMeaning;
    private boolean revocationModificationEnabled;
    private boolean processRevokesImmediately;
    private String defaultRevocationModificationOperation;
    private boolean useLinkAttributeValueForRevocationModification;
    private boolean showRoleRevocationDetails;
    private List<String> bulkDecisions;
    private List<String> bulkEntityDecisions;
    private boolean allowExceptionPopup;
    private Duration allowExceptionDuration;
    private boolean promptForSignoff;
    private boolean showRecommendations;
    private boolean autoApprove;
    private boolean includeClassifications;

    /**
     * Constructor
     * @param certification Certification object 
     * @param userContext UserContext
     * @throws GeneralException
     */
    public CertificationConfigDTO(Certification certification, UserContext userContext) throws GeneralException {
        if (certification == null) {
            throw new GeneralException("certification is required");
        }
        if (userContext == null) {
            throw new GeneralException("userContext is required");
        }

        SailPointContext context = userContext.getContext();
        CertificationDefinition definition = certification.getCertificationDefinition(context);

        this.statusesRequiringComments = definition.getStatusesRequiringComments(context);
        this.esigMeaning = new Notary(context, userContext.getLocale()).getSignatureMeaning(certification);
        this.revocationModificationEnabled = calculateRevocationModificationEnabled(context);
        this.processRevokesImmediately = definition.isProcessRevokesImmediately();
        this.bulkDecisions = this.populateAvailableBulkDecisions(context, definition);
        this.bulkEntityDecisions = this.populateAvailableBulkEntityDecisions(context, definition);
        Configuration configuration = context.getConfiguration();
        this.showRoleRevocationDetails = configuration.getBoolean(Configuration.SHOW_CERTIFICATION_ROLE_REVOCATION_DETAILS, false);
        if (this.revocationModificationEnabled) {
            this.defaultRevocationModificationOperation = configuration.getString(Configuration.DEFAULT_REMEDIATION_MODIFIABLE_OP);
            if (this.defaultRevocationModificationOperation == null) {
                this.defaultRevocationModificationOperation = ProvisioningPlan.Operation.Set.toString();
            }
            
            this.useLinkAttributeValueForRevocationModification = !configuration.getBoolean(Configuration.USE_MANAGED_ATTRIBUTES_FOR_REMEDIATION, true);
        }

        this.allowExceptionDuration = definition.getAllowExceptionDuration(context);
        this.allowExceptionPopup = definition.isAllowExceptionPopup() != null ? definition.isAllowExceptionPopup() : false;
        this.promptForSignoff = definition.isAutomateSignoffPopup() != null ? definition.isAutomateSignoffPopup() : false;
        this.showRecommendations = definition.getShowRecommendations() != null ? definition.getShowRecommendations() : false;
        this.autoApprove = definition.isAutoApprove() != null ? definition.isAutoApprove() : false;
        this.includeClassifications = definition.isIncludeClassifications() != null ? definition.isIncludeClassifications() : false;
    }

    /**
     * Populate a list of available bulk decisions
     * @param context SailPointContext
     * @param definition CertificationDefinition
     * @return List<String> list of bulk decision strings
     * @throws GeneralException
     */
    private List<String> populateAvailableBulkDecisions(
            SailPointContext context, 
            CertificationDefinition definition) throws GeneralException {

        List<String> availableBulkDecisions = new ArrayList<String>();
        if (definition.isAllowListBulkApprove(context)) {
            availableBulkDecisions.add(CertificationAction.Status.Approved.name());
        }
        if (definition.isAllowListBulkRevoke(context)) {
            availableBulkDecisions.add(CertificationAction.Status.Remediated.name());
        }
        if (definition.isAllowListBulkAccountRevocation(context)) {
            availableBulkDecisions.add(CertificationAction.Status.RevokeAccount.name());
        }
        if (definition.isAllowListBulkMitigate(context)) {
            availableBulkDecisions.add(CertificationAction.Status.Mitigated.name());
        }
        if (definition.isAllowListBulkReassign(context)) {
            availableBulkDecisions.add(CertificationAction.CERT_BULK_DECISION_REASSIGN);
        }
        if (definition.isAllowListBulkClearDecisions(context)) {
            availableBulkDecisions.add(CertificationAction.Status.Cleared.name());
        }
        return availableBulkDecisions;
    }

    /**
     * Populate a list of available bulk decisions
     * @param context SailPointContext
     * @param definition CertificationDefinition
     * @return List<String> list of bulk decision strings
     * @throws GeneralException
     */
    private List<String> populateAvailableBulkEntityDecisions(
            SailPointContext context,
            CertificationDefinition definition) throws GeneralException {

        List<String> decisions  = new ArrayList<>();
        if (definition.isAllowListBulkReassign(context)) {
            decisions.add(CertificationAction.CERT_BULK_DECISION_REASSIGN);
        }
        if (definition.isAllowEntityDelegation(context)) {
            decisions.add(CertificationAction.Status.Delegated.name());
        }
        return decisions;
    }

    /**
     * Calculate whether revocation modification is enabled for any applications.  We could potentially try to constrain
     * this down to only the apps that are represented in the cert, but we'll keep it simple.
     */
    private boolean calculateRevocationModificationEnabled(SailPointContext context) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.or(getRevocationModificationFilter("schemas.attributes.remediationModificationType"),
                         getRevocationModificationFilter("schemas.permissionsRemediationModificationType")));
        int count = context.countObjects(Application.class, qo);
        return (count > 0);
    }

    /**
     * Create a filter that checks for revocation modification enablement on an application with the given property.
     */
    private static Filter getRevocationModificationFilter(String prop) {
        return Filter.and(Filter.ne(prop, UserInterfaceInputType.None), Filter.notnull(prop));
    }

    /**
     * Get the statuses that require a comment along with decision
     * @return List of CertificationAction.Status values
     */
    public List<CertificationAction.Status> getStatusesRequiringComments() {
        return statusesRequiringComments;
    }

    /**
     * Get the electronic signature meaning. May be null if none is defined.
     * @return String meaning message
     */
    public String getEsigMeaning() {
        return this.esigMeaning;
    }

    /**
     * Return whether any applications in the system support modifiable revocations.
     */
    public boolean isRevocationModificationEnabled() {
        return revocationModificationEnabled;
    }

    /**
     * @return true if process revokes immediately is set on this certification
     */
    public boolean isProcessRevokesImmediately() {
        return processRevokesImmediately;
    }

    /**
     * @return Name of operation to use initially for remediation modifiable purposes. Will be null if 
     * revocation modification is not enabled.
     */
    public String getDefaultRevocationModificationOperation() {
        return this.defaultRevocationModificationOperation;
    }

    /**
     * @return true if link attribute suggest should be used for revocation modification instead of managed attributes
     */
    public boolean isUseLinkAttributeValueForRevocationModification() {
        return useLinkAttributeValueForRevocationModification;
    }

    /**
     * @return true if we need to show the role revocation details in a dialog
     */
    public boolean isShowRoleRevocationDetails() {
        return showRoleRevocationDetails;
    }

    /**
     * @return the list of allowed bulk decisions
     */
    public List<String> getBulkDecisions() {
        return bulkDecisions;
    }

    /**
     * @return the list of allowed bulk entity decisions
     */
    public List<String> getBulkEntityDecisions() {
        return bulkEntityDecisions;
    }

    /**
     * Convert the allow exception duration into an actual date.
     *
     * @return {Date}
     */
    public Date getAllowExceptionDate() {
        return this.allowExceptionDuration != null ? this.allowExceptionDuration.addTo(new Date()) : null;
    }

    /**
     * @return {Boolean} True if we allow the allow exception popup.
     */
    public boolean getAllowExceptionPopup() {
        return this.allowExceptionPopup;
    }

    /**
     * @return {Boolean} True if prompt for signoff option is enabled.
     */
    public boolean isPromptForSignoff() {
        return promptForSignoff;
    }

    /**
     *
     * @return {Boolean} True if recommendations should be shown. Otherwise false.
     */
    public boolean getShowRecommendations() { return this.showRecommendations; }


    public boolean isAutoApprove() { return this.autoApprove; }

    public boolean isIncludeClassifications() {
        return this.includeClassifications;
    }

    public void setIncludeClassifications(boolean includeClassifications) {
        this.includeClassifications = includeClassifications;
    }

}
