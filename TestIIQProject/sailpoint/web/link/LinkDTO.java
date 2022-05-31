/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.link;

import java.util.Date;
import java.util.List;
import java.util.Map;

import sailpoint.object.ColumnConfig;
import sailpoint.object.Link;
import sailpoint.object.ApprovalItem.ProvisioningState;
import sailpoint.object.WorkItem;
import sailpoint.service.BaseDTO;


/**
 * @author: cindy.he
 * A DTO to represent the LinkDTO model.
 * 
 * It gives some methods to the ui tier about the types of items
 * in the LinkDTO.  
 */
public class LinkDTO extends BaseDTO {
    private String accountId; 
    private String applicationName;   
    private String identityId;
    private AccountStatus status;
    private ProvisioningState actionStatus;
    private WorkItem.State approvalStatus;
    private Date lastRefresh;
    private Date passwordChangeDate;

    /**
     * Constructor, call parent's constructor
     * @param link link Object
     * @param linkMap a link object stored in map, object properties are used for key
     * @param cols list of UI ColumnConfigs of the projection columns
     */
    public LinkDTO(Link link, Map<String, Object>linkMap, List<ColumnConfig> cols) {
        super(linkMap, cols);
        initialize(link);
    }

    public LinkDTO(Link link) {
        super(link.getId());
        initialize(link);
    }

    /**
     * Initialize properties
     * @param link link Object
     */
    private void initialize(Link link) {
        this.accountId = link.getDisplayableName();
        this.applicationName = link.getApplicationName();
        if(link.getIdentity()!=null) {
            this.identityId = link.getIdentity().getId();
        }
        this.lastRefresh = link.getLastRefresh();
        this.status = getAccountStatus(link);
    }
    
    /**
     * check the account status of given account
     * @param link link of given identity
     * @return string account status
     */
    private AccountStatus getAccountStatus(Link link) {
        if (link.isDisabled()) {
            return AccountStatus.DISABLED;
        }
        else if (link.isLocked()) {
            return AccountStatus.LOCKED;
        }
        else {
            return AccountStatus.ACTIVE;
        }
    }
    
    /**
     * get account's application name
     * @return application name 
     */
    public String getApplicationName() {
        return applicationName;
    }

    /**
     * set account's application name
     * @param applicationName application name
     */
    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    /**
     * get account status
     * @return status
     */
    public AccountStatus getStatus() {
        return status;
    }

    /**
     * set account status
     * @param status account status
     */
    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    /**
     * get last refresh date
     * @return last refresh date
     */
    public Date getLastRefresh() {
        return lastRefresh;
    }

    /**
     * set last refresh date
     * @param lastRefresh last refresh date
     */
    public void setLastRefresh(Date lastRefresh) {
        this.lastRefresh = lastRefresh;
    }

    /**
     * get account id, this is the account display name
     * @return account id
     */
    public String getAccountId() {
        return accountId;
    }

    /**
     * set account id, this is the account display name
     * @param accountId account display name
     */
    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    /**
     * get link owner's identity id
     * @return identity id
     */
    public String getIdentityId() {
        return identityId;
    }

    /**
     * set link owner's identity id
     * @param identityId identity id
     */
    public void setIdentityId(String identityId) {
        this.identityId = identityId;
    }   

    /**
     * Get previous change password operation status for given account
     * @return previous change password status
     */
    public ProvisioningState getActionStatus() {
        return actionStatus;
    }

    /**
     * set previous change password operation status for given account
     * @param actionStatus previous change password operation status for given account
     */
    public void setActionStatus(ProvisioningState actionStatus) {
        this.actionStatus = actionStatus;
    }

    /**
     * Get the approval status of a previous change
     * @return approval status of a previous change
     */
    public WorkItem.State getApprovalStatus() {
        return approvalStatus;
    }

    /**
     * set approval status of a previous change
     * @param approvalStatus approval status of a previous change
     */
    public void setApprovalStatus(WorkItem.State approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    /**
     * Get the password change date of a link - taken from IdentityRequestItem
     * @return passwordChangeDate
     */
    public Date getPasswordChangeDate() {
        return passwordChangeDate;
    }

    /**
     * Set the passwordChangeDate
     * @param passwordChangeDate
     */
    public void setPasswordChangeDate(Date passwordChangeDate) {
        this.passwordChangeDate = passwordChangeDate;
    }
    /**
     * Enum for account status 
     * @author cindy.he
     *
     */
    public enum AccountStatus {
        DISABLED,
        LOCKED,
        ACTIVE
    }
}
