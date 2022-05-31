/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.certification;

import sailpoint.api.Iconifier.Icon;
import sailpoint.object.CertificationItem;
import sailpoint.object.ColumnConfig;
import sailpoint.recommender.RecommendationDTO;
import sailpoint.service.BaseDTO;
import sailpoint.service.policyviolation.PolicyViolationDTO;
import sailpoint.tools.MessageKeyHolder;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.view.certification.CertificationDecisionStatus;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * DTO to represent a generic CertificationItem object. It holds common fields and type of the certification item.
 * Fields specific to individual types of certification items should be in subclasses.
 */
public class CertificationItemDTO extends BaseDTO {

    /**
     * Enumeration of values for Changes Detected column. Note we dont have a property for this,
     * it is just shoved in the attributes map as a localized string, but the enumeration is valuable
     * for filtering. 
     */
    public enum ChangesDetected implements MessageKeyHolder {
        No(MessageKeys.NO),
        Yes(MessageKeys.YES),
        NewUser(MessageKeys.CERT_NEW_USER);
        
        private String messageKey;
        
        ChangesDetected(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return this.messageKey;
        }
    }

    /**
     * Type of CertificationItem
     */
    private CertificationItem.Type type;

    /**
     * Sub-type of CertificationItem.
     */
    private CertificationItem.SubType subType;

    /**
     * Display name of the object being certified
     */
    private String displayName;

    /**
     * Description of the object being certified
     */
    private String description;

    /**
     * Decision status, including current state
     */
    private CertificationDecisionStatus decisionStatus;

    /**
     * Summary status for the item.
     */
    private CertificationItem.Status summaryStatus;

    /**
     * Policy violation DTO
     */
    private PolicyViolationDTO policyViolationDTO;

    /**
     * Name of the role
     */
    private String roleName;

    /**
     * Application(s) from the target accounts of the role assignment
     */
    private String roleApplications;

    /**
     * Account name(s) from the target accounts of the role assignment
     */
    private String roleAccountNames;

    /**
     * Name of the application. Applies only to entitlement/account items.
     */
    private String application;

    /**
     * Native identity of the account. Applies only to entitlement/account items.
     */
    private String nativeIdentity;

    /**
     * Display name of the account. Applies only to entitlement/account items.
     */
    private String accountName;

    /**
     * Instance of the account. Applies only to entitlement/account items.
     */
    private String instance;

    /**
     * Name of the attribute (or permission target). Applies only to entitlement items.
     */
    private String attribute;

    /**
     * Value of the attribute (or permission rights). Applies only to entitlement items.
     */
    private String value;

    /**
     * Flag indicating this is a permission or not.
     */
    private boolean permission;

    /**
     * Flag indicating this is a group attribute or not.
     * As of 8.1 this is a bit of misnomer, we used this flag to indicate that this has
     * a ManagedAttribute in the system corresponding to the item, but it can be a permission too.
     */
    private boolean groupAttribute;

    /**
     * The ID of the parent certification entity.
     */
    private String entityId;

    /**
     * Challenge owner name
     */
    private String challengeOwnerName;

    /**
     * Challenge comment
     */
    private String challengeComment;

    /**
     * Challenge decision, accept or reject message key
     */
    private String challengeDecision;

    /**
     * Challenge decision comment
     */
    private String challengeDecisionComment;

    /**
     * Challenge decider name
     */
    private String challengeDeciderName;

    /**
     * The comments from the delegatee from completing or returning a delegation.
     */
    private String delegationCompletionComments;

    /**
     * The display name of the user that completed or returned the delegation.
     */
    private String delegationCompletionUser;

    /**
     * Flag indicating whether review is required. Note this can be true even if canChangeDecision is false, in which
     * case only UI actions related to review (e.g., Keep) will be available.
     */
    private boolean requiresReview;

    /**
     * Flag indicating whether challenge decision is required. Note this can be true even if canChangeDecision 
     * is false, in which case only UI actions related to challenge will be available.
     */
    private boolean requiresChallengeDecision;

    /**
     * Flag indicating whether decisions can be changed.
     */
    private boolean canChangeDecision;

    /**
     * If the previous decision was a revoke and not processed yet
     */
    private boolean unremovedRemediation;

    /**
     * If the previous decision was a mitigation that has not expired
     */
    private boolean currentMitigation;

    /**
     * If the previous decision was a mitigation that has expired
     */
    private boolean expiredMitigation;

    /**
     * If the previous decision was an approved with some missing items required as well
     */
    private boolean provisionAddsRequest;

    /**
     * Date of the last mitigation decision
     */
    private Date lastMitigationDate;

    /**
     * List containing the name of the policy violation/s that the cert item is included in
     */
    private List<String> policyViolations;

    /**
     *  List of icon/s for the account status
     */
    private List<Icon> accountStatusIcons;

    /**
     * The decision details
     */
    private BaseDecision decision;

    /**
     * Recommendation details if there is a recommendation for this cert item
     */
    private RecommendationDTO recommendation;

    /**
     * List of classification names for this cert item
     */
    private List<String> classificationNames;

    /**
     * Map Constructor
     * @param certificationItem Map representing the CertificationItem from the ColumnConfig
     * @param cols List of ColumnConfigs for the CertificationItem map
     * @param additionalColumns List of names of additional columns to be part of this dto
     */
    public CertificationItemDTO(Map<String,Object> certificationItem, List<ColumnConfig> cols, List<String> additionalColumns) {
        super(certificationItem, cols, additionalColumns);
    }

    public CertificationItem.Type getType() {
        return type;
    }

    public void setType(CertificationItem.Type type) {
        this.type = type;
    }

    public CertificationItem.SubType getSubType() {
        return subType;
    }

    public void setSubType(CertificationItem.SubType subType) {
        this.subType = subType;
    }

    /**
     * Return a message key that can be translated to a user-friendly name of the getType() value.
     */
    public String getTypeMessageKey() {
        return (null != this.type) ? this.type.getShortMessageKey() : null;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public CertificationDecisionStatus getDecisionStatus() {
        return decisionStatus;
    }

    public void setDecisionStatus(CertificationDecisionStatus decisionStatus) {
        this.decisionStatus = decisionStatus;
    }

    public CertificationItem.Status getSummaryStatus() {
        return summaryStatus;
    }

    public void setSummaryStatus(CertificationItem.Status summaryStatus) {
        this.summaryStatus = summaryStatus;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public String getNativeIdentity() {
        return nativeIdentity;
    }

    public void setNativeIdentity(String nativeIdentity) {
        this.nativeIdentity = nativeIdentity;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean isPermission() {
        return permission;
    }

    public void setPermission(boolean permission) {
        this.permission = permission;
    }

    public boolean isGroupAttribute() {
        return groupAttribute;
    }

    public void setGroupAttribute(boolean groupAttribute) {
        this.groupAttribute = groupAttribute;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getChallengeOwnerName() {
        return challengeOwnerName;
    }

    public void setChallengeOwnerName(String challengeOwnerName) {
        this.challengeOwnerName = challengeOwnerName;
    }

    public String getChallengeComment() {
        return challengeComment;
    }

    public void setChallengeComment(String challengeComment) {
        this.challengeComment = challengeComment;
    }

    public String getChallengeDecision() {
        return challengeDecision;
    }

    public void setChallengeDecision(String challengeDecision) {
        this.challengeDecision = challengeDecision;
    }

    public String getChallengeDecisionComment() {
        return challengeDecisionComment;
    }

    public void setChallengeDecisionComment(String challengeDecisionComment) {
        this.challengeDecisionComment = challengeDecisionComment;
    }

    public String getChallengeDeciderName() {
        return challengeDeciderName;
    }

    public void setChallengeDeciderName(String challengeDeciderName) {
        this.challengeDeciderName = challengeDeciderName;
    }

    public String getDelegationCompletionComments() {
        return delegationCompletionComments;
    }

    public void setDelegationCompletionComments(String comments) {
        this.delegationCompletionComments = comments;
    }

    public String getDelegationCompletionUser() {
        return delegationCompletionUser;
    }

    public void setDelegationCompletionUser(String delegationCompletionUser) {
        this.delegationCompletionUser = delegationCompletionUser;
    }

    public boolean isRequiresReview() {
        return requiresReview;
    }

    public void setRequiresReview(boolean requiresReview) {
        this.requiresReview = requiresReview;
    }

    public boolean isCanChangeDecision() {
        return canChangeDecision;
    }

    public void setCanChangeDecision(boolean canChangeDecision) {
        this.canChangeDecision = canChangeDecision;
    }

    public boolean isUnremovedRemediation() {
        return unremovedRemediation;
    }

    public void setUnremovedRemediation(boolean unremovedRemediation) {
        this.unremovedRemediation = unremovedRemediation;
    }

    public boolean isCurrentMitigation() {
        return currentMitigation;
    }

    public void setCurrentMitigation(boolean currentMitigation) {
        this.currentMitigation = currentMitigation;
    }

    public boolean isExpiredMitigation() {
        return expiredMitigation;
    }

    public void setExpiredMitigation(boolean expiredMitigation) {
        this.expiredMitigation = expiredMitigation;
    }

    public boolean isProvisionAddsRequest() {
        return provisionAddsRequest;
    }

    public void setProvisionAddsRequest(boolean provisionAddsRequest) {
        this.provisionAddsRequest = provisionAddsRequest;
    }

    public Date getLastMitigationDate() {
        return lastMitigationDate;
    }

    public void setLastMitigationDate(Date lastMitigationDate) {
        this.lastMitigationDate = lastMitigationDate;
    }

    public List<String> getPolicyViolations() {
        return policyViolations;
    }

    public void setPolicyViolations(List<String> policyViolations) {
        this.policyViolations = policyViolations;
    }

    public List<Icon> getAccountStatusIcons() {
        return accountStatusIcons;
    }

    public void setAccountStatusIcons(List<Icon> accountStatusIcons) {
        this.accountStatusIcons = accountStatusIcons;
    }

    public BaseDecision getDecision() {
        return decision;
    }

    public void setDecision(BaseDecision decision) {
        this.decision = decision;
    }

    public String getRoleApplications() {
        return roleApplications;
    }

    public void setRoleApplications(String roleApplications) {
        this.roleApplications = roleApplications;
    }

    public String getRoleAccountNames() {
        return roleAccountNames;
    }

    public void setRoleAccountNames(String roleAccountNames) {
        this.roleAccountNames = roleAccountNames;
    }

    public boolean isRequiresChallengeDecision() {
        return requiresChallengeDecision;
    }

    public void setRequiresChallengeDecision(boolean requiresChallengeDecision) {
        this.requiresChallengeDecision = requiresChallengeDecision;
    }

    public PolicyViolationDTO getPolicyViolationDTO() {
        return policyViolationDTO;
    }

    public void setPolicyViolationDTO(PolicyViolationDTO policyViolationDTO) {
        this.policyViolationDTO = policyViolationDTO;
    }

    public RecommendationDTO getRecommendation() { return recommendation; }

    public void setRecommendation(RecommendationDTO rec) { recommendation = rec; }

    public List<String> getClassificationNames() {
        return classificationNames;
    }

    public void setClassificationNames(List<String> classificationNames) {
        this.classificationNames = classificationNames;
    }
}
