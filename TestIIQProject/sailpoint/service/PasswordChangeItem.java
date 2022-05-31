package sailpoint.service;

import sailpoint.web.link.LinkDTO;

/**
 * PasswordChangeItem is used to pass a link and associated password
 * back to the client side during a password change action
 */
public class PasswordChangeItem {
    private final LinkDTO link;
    private final String password;
    private final String status;

    /**
     * Constructor
     * @param link The id of the link
     * @param password The links new password
     * @param status The status of the password change
     */
    public PasswordChangeItem(LinkDTO link, String password, String status) {
        this.link = link;
        this.password = password;
        this.status = status;
    }

    /**
     * The link
     * @return The link
     */
    public LinkDTO getLink() {
        return link;
    }

    /**
     * Utility function for accessing the link id
     * @return The id of the link
     */
    public String getLinkId() {
        if(this.link !=null) {
            return this.link.getId();
        }
        return null;
    }

    /**
     * The new password
     * @return The new password
     */
    public String getPassword() {
        return password;
    }

    /**
     * The status of the password change
     * @return The status of the password change
     */
    public String getStatus() {
        return status;
    }
}
