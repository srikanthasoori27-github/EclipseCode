/*
 * (c) Copyright 2016. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.certification;

import java.util.List;
import java.util.Map;

import sailpoint.object.AbstractCertificationItem;
import sailpoint.object.CertificationDelegation;
import sailpoint.object.CertificationEntity;
import sailpoint.object.ColumnConfig;
import sailpoint.object.IdentityDifference;
import sailpoint.service.BaseDTO;
import sailpoint.service.IdentityAttributesDTO;
import sailpoint.service.ScorecardDTO;

/**
 * DTO to represent a CertificationEntity for the UI
 */
public class CertificationEntityDTO extends BaseDTO {
    
    private String targetId;
    private CertificationItemStatusCount itemStatusCount = new CertificationItemStatusCount();
    private IdentityAttributesDTO identityAttributes;
    private ScorecardDTO scorecard;
    private IdentityDifference differences;
    private CertificationEntity.Type type;
    private String description;
    private String displayableName;
    private String attribute;
    private CertificationDelegation delegation;
    private AbstractCertificationItem.Status summaryStatus;
    private int certificationItemCount;
    private String permission;
    private String nativeIdentity;
    private String accountGroup;
    private String email;
    private String identityName;
    private boolean hasAutoApprovals;

    /**
     * Constructor
     *
     * @param entity CertificationEntity object
     */
    public CertificationEntityDTO(CertificationEntity entity) {
        super(entity.getId());
        this.certificationItemCount = entity.getItems().size();
        this.targetId = entity.getTargetId();
        this.displayableName = entity.getTargetDisplayName();
        this.type = entity.getType();
        this.accountGroup = entity.getAccountGroup();
        this.nativeIdentity = entity.getNativeIdentity();
    }

    /**
     * Constructor
     * @param entityMap CertificationEntity object map
     * @param cols column configs
     */
    public CertificationEntityDTO(Map<String, Object> entityMap, List<ColumnConfig> cols) {
        super(entityMap, cols);
    }

    public void setItemStatusCount(CertificationItemStatusCount itemStatusCount) {
        this.itemStatusCount = itemStatusCount;
    }

    /**
     * Get the item status counts for the entity
     * @return CertificationItemStatusCount itemStatusCount for the entity
     */
    public CertificationItemStatusCount getItemStatusCount() {
        return itemStatusCount;
    }

    /**
     * Get the identity details if this CertificationEntity is an identity type.
     * @return The IdentityAttributesDTO for the entity, or null.
     */
    public IdentityAttributesDTO getIdentityAttributes() {
        return identityAttributes;
    }

    /**
     * Set the identity details for this entity.  This is not done in the constructor because we do
     * not want to incur the cost of loading this when we want a light version of this DTO.
     */
    public void setIdentityAttributes(IdentityAttributesDTO attributes) {
        this.identityAttributes = attributes;
    }

    /**
     * Get the scorecard if this CertificationEntity is an identity type.
     * @return The ScorecardDTO for the entity, or null.
     */
    public ScorecardDTO getScorecard() {
        return scorecard;
    }

    /**
     * Set the scorecard for this entity.  This is not done in the constructor because we do
     * not want to incur the cost of loading this when we want a light version of this DTO.
     */
    public void setScorecard(ScorecardDTO scorecard) {
        this.scorecard = scorecard;
    }

    /**
     * Get the differences if this CertificationEntity is difference-able.
     * @return The IdentityDifference for the entity, or null.
     */
    public IdentityDifference getDifferences() {
        return differences;
    }

    /**
     * Set the differences for this entity.  This is not done in the constructor because we do
     * not want to incur the cost of loading this when we want a light version of this DTO.
     */
    public void setDifferences(IdentityDifference differences) {
        this.differences = differences;
    }

    /**
     * 
     * @return the name of the associated targetId 
     */
    public String getTargetId() {
        return targetId;
    }

    /**
     * 
     * @param targetId the name of the associated targetId
     */
    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    /**
     * 
     * @return certification entity type
     */
    public CertificationEntity.Type getType() {
        return type;
    }

    /**
     * 
     * @param type Certification type used to set certification entity type
     */
    public void setType(CertificationEntity.Type type) {
        this.type = type;
    }

    /**
     * 
     * @return entitlement description
     */
    public String getDescription() {
        return description;
    }

    /**
     * set entitlement description
     * @param description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 
     * @return entitlement displayable name
     */
    public String getDisplayableName() {
        return displayableName;
    }

    /**
     * set entitlement displayable name
     * @param displayableName
     */
    public void setDisplayableName(String displayableName) {
        this.displayableName = displayableName;
    }

    /**
     * 
     * @return entitlement attribute
     */
    public String getAttribute() {
        return attribute;
    }

    /**
     * set entitlement attribute
     * @param attribute
     */
    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    /**
     * 
     * @return delegation certification delegation
     */
    public CertificationDelegation getDelegation() {
        return delegation;
    }

    /**
     * set certificaiton delegation
     * @param delegation certificaiton delegation
     */
    public void setDelegation(CertificationDelegation delegation) {
        this.delegation = delegation;
    }

    /**
     * 
     * @return status summary
     */
    public AbstractCertificationItem.Status getSummaryStatus() {
        return summaryStatus;
    }

    /**
     * set status summary
     * @param summaryStatus
     */
    public void setSummaryStatus(AbstractCertificationItem.Status summaryStatus) {
        this.summaryStatus = summaryStatus;
    }

    /**
     * 
     * @return certification item count in the entity
     */
    public int getCertificationItemCount() {
        return this.certificationItemCount;
    }

    /**
     * set certification item count in the entity
     * @param count certification item count in the entity
     */
    public void setCertificationItemCount(int count) {
        this.certificationItemCount = count;
    }

    /**
     * 
     * @return permission on the entity
     */
    public String getPermission() {
        return permission;
    }

    /**
     * Set permission on entity
     * @param permission on entity
     */
    public void setPermission(String permission) {
        this.permission = permission;
    }

    /**
     *
     * @return native identity
     */
    public String getNativeIdentity() {
        return nativeIdentity;
    }

    /**
     * Set native identity
     * @param nativeIdentity native identity
     */
    public void setNativeIdentity(String nativeIdentity) {
        this.nativeIdentity = nativeIdentity;
    }

    /**
     *
     * @return account group
     */
    public String getAccountGroup() {
        return accountGroup;
    }

    /**
     * Set account group
     * @param accountGroup account group
     */
    public void setAccountGroup(String accountGroup) {
        this.accountGroup = accountGroup;
    }

    /**
     *
     * @return the identity email address, or null if it doesn't exist or this isn't an identity-based entity.
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets the email address
     * @param email email address
     */
    public void setEmail(String email) {
        this.email = email;
    }

    public String getIdentityName() {
        return identityName;
    }

    public void setIdentityName(String identityName) {
        this.identityName = identityName;
    }

    public boolean getHasAutoApprovals() { return this.hasAutoApprovals; }

    public void setHasAutoApprovals(boolean hasAutoApprovals) { this.hasAutoApprovals = hasAutoApprovals; }
}
