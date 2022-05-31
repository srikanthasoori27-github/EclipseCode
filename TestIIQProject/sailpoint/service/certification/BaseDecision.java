package sailpoint.service.certification;

import sailpoint.service.IdentitySummaryDTO;
import sailpoint.tools.GeneralException;
import sailpoint.web.view.LineItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A base Decision class that can be used outside of CertificationDecisioner to include in DTOs, etc. This contains
 * just the subset of Decision attributes that are interesting to the UI. The decisioner Decision inner class can
 * extend this to build a more fleshed out version for decision processing.
 */
public class BaseDecision {

    public static final String COMMENTS = "comments";
    public static final String DESCRIPTION = "description";
    public static final String MITIGATION_EXPIRATION_DATE = "mitigationExpirationDate";
    public static final String RECIPIENT = "recipient";
    public static final String REMEDIATION_DETAILS = "remediationDetails";
    public static final String REVOKED_ROLES = "revokedRoles";
    public static final String SELECTED_ENTITLEMENTS = "selectedViolationEntitlements";
    public static final String AUTO_DECISION = "autoDecision";

    public static final String STATUS = "status";

    /**
     * The decision comments.
     */
    private String comments;

    /**
     * The description.
     */
    private String description;

    /**
     * The expiration data for mitigation (allow exceptions).
     */
    private Long mitigationExpirationDate;

    /**
     * The id of the recipient identity for delegation/reassign.
     */
    private String recipient;

    /**
     * The summary of the recipient.
     */
    private IdentitySummaryDTO recipientSummary;

    /**
     * List of names of revoked roles.
     */
    private List<String> revokedRoles;

    /**
     * List of LineItems for the remediation modifiable details
     */
    private List<LineItem> remediationDetails;

    /**
     * JSON encoded list of selected violation entitlements
     */
    private String selectedViolationEntitlements;

    /**
     * The decision status, a CertificationAction.Status value.
     */
    private String status;

    /**
     * Whether the decision was automatically set.
     */
    private boolean autoDecision;

    public BaseDecision() {}

    public BaseDecision(Map<String, Object> decisionMap) throws GeneralException {

        this.status = (String)decisionMap.get(STATUS);
        this.comments = (String)decisionMap.get(COMMENTS);
        this.recipient = (String)decisionMap.get(RECIPIENT);

        if (decisionMap.containsKey(REVOKED_ROLES)) {
            this.revokedRoles = (List<String>)decisionMap.get(REVOKED_ROLES);
        }

        if (decisionMap.containsKey(DESCRIPTION)) {
            this.description = (String)decisionMap.get(DESCRIPTION);
        }

        if (decisionMap.containsKey(MITIGATION_EXPIRATION_DATE)) {
            this.mitigationExpirationDate = (Long)decisionMap.get(MITIGATION_EXPIRATION_DATE);
        }

        if (decisionMap.containsKey(REMEDIATION_DETAILS)) {
            for (Map<String, Object> lineItem : (List<Map<String, Object>>)decisionMap.get(REMEDIATION_DETAILS)) {
                if (this.remediationDetails == null) {
                    this.remediationDetails = new ArrayList<LineItem>();
                }
                this.remediationDetails.add(new LineItem(lineItem));
            }
        }

        if (decisionMap.containsKey(SELECTED_ENTITLEMENTS)) {
            this.selectedViolationEntitlements = (String)decisionMap.get(SELECTED_ENTITLEMENTS);
        }

        if (decisionMap.containsKey(AUTO_DECISION)) {
            this.autoDecision = (boolean)decisionMap.get(AUTO_DECISION);
        }
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getMitigationExpirationDate() {
        return mitigationExpirationDate;
    }

    public void setMitigationExpirationDate(Long mitigationExpirationDate) {
        this.mitigationExpirationDate = mitigationExpirationDate;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public List<String> getRevokedRoles() {
        return revokedRoles;
    }

    public void setRevokedRoles(List<String> revokedRoles) {
        this.revokedRoles = revokedRoles;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public IdentitySummaryDTO getRecipientSummary() {
        return recipientSummary;
    }

    public void setRecipientSummary(IdentitySummaryDTO recipientSummary) {
        this.recipientSummary = recipientSummary;
    }

    public List<LineItem> getRemediationDetails() {
        return remediationDetails;
    }

    public void setRemediationDetails(List<LineItem> remediationDetails) {
        this.remediationDetails = remediationDetails;
    }
    
    public String getSelectedViolationEntitlements() {
        return selectedViolationEntitlements;
    }

    public void setSelectedViolationEntitlements(String selectedViolationEntitlements) {
        this.selectedViolationEntitlements = selectedViolationEntitlements;
    }

    public boolean isAutoDecision() { return autoDecision; }

    public void setAutoDecision(boolean autoDecision) { this.autoDecision = autoDecision; }
}
