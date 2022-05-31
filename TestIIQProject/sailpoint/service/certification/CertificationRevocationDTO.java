/*
 * (c) Copyright 2016. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.certification;

/**
 * @author patrick.jeong
 */
public class CertificationRevocationDTO {
    /**
     * The display name of certification item target
     */
    private String identityName;

    /**
     * The display name of role or entitlement
     */
    private String targetDisplayName;

    /**
     * Action details
     */
    private String details;

    /**
     * Calculated remediation action type
     */
    private String requestType;

    /**
     * The display name of cert owner
     */
    private String requester;

    /**
     * The display name of work item owner
     */
    private String owner;

    /**
     * Localized expiration date
     */
    private String expiration;

    /**
     * Either "Open" or "Closed" based on action.remediationCompleted flag
     */
    private String status;

    /**
     * Constructor
     */
    public CertificationRevocationDTO() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIdentityName() {
        return identityName;
    }

    public void setIdentityName(String identityName) {
        this.identityName = identityName;
    }

    public String getTargetDisplayName() {
        return targetDisplayName;
    }

    public void setTargetDisplayName(String targetDisplayName) {
        this.targetDisplayName = targetDisplayName;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getRequestType() {
        return requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    public String getRequester() {
        return requester;
    }

    public void setRequester(String requester) {
        this.requester = requester;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getExpiration() {
        return expiration;
    }

    public void setExpiration(String expiration) {
        this.expiration = expiration;
    }
}
