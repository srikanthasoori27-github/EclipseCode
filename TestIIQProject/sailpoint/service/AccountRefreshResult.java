package sailpoint.service;

import sailpoint.web.link.AccountLinkDTO;

/**
 * An AccountRefreshResult is returned from an attempt to refresh the status of an account.
 * This will either have an error (error will be non-null or deleted will be true), or will have the
 * refreshed account.
 */
public class AccountRefreshResult {

    private String id;
    private AccountLinkDTO account;
    private boolean deleted;
    private String error;

    /**
     * Constructor.
     *
     * @param id  The ID of the Link.
     */
    public AccountRefreshResult(String id) {
        this.id = id;
    }

    /**
     * @return True if the refresh did not result in an error.
     */
    public boolean wasRefreshed() {
        return !this.deleted && (null == this.error);
    }

    /**
     * @return The ID of the Link.
     */
    public String getId() {
        return id;
    }

    /**
     * @return The refreshed AccountLinkDTO if there was not an error refreshing, null otherwise.
     */
    public AccountLinkDTO getAccount() {
        return account;
    }

    public void setAccount(AccountLinkDTO account) {
        this.account = account;
    }

    /**
     * @return True if we detected that the account was deleted when refreshing.
     */
    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    /**
     * @return The localized error if an error occurred while refreshing.
     */
    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}