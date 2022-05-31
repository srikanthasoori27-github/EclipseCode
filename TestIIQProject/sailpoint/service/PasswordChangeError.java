/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service;

import sailpoint.web.link.LinkDTO;

import java.util.List;

/**
 * @author: peter.holcomb
 *
 * This class models an error that has resulted from a password change
 */
public class PasswordChangeError {

    private LinkDTO link;
    private List<String> messages;
    private boolean isConstraintsViolation;

    /**
     * Constructor
     * @param link The link associated with the password change
     * @param messages Error messages associated with the password change error
     * @param isConstraintsViolation Whether the password change resulted from a violation of the password policy constraints
     */
    public PasswordChangeError(LinkDTO link, List<String> messages, boolean isConstraintsViolation) {
        this.link = link;
        this.messages = messages;
        this.isConstraintsViolation = isConstraintsViolation;
    }

    /**
     * The link that is associated with this password change
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

    public void setLink(LinkDTO link) {
        this.link = link;
    }

    /**
     * A list of any error messages associated with the password change
     * @return The list of messages
     */
    public List<String> getMessages() {
        return messages;
    }

    public void setMessages(List<String> messages) {
        this.messages = messages;
    }

    /**
     * Whether the error occurred due to a violation of the password policy's constraints
     * @return Whether the error is a result of a password policy's constraints
     */
    public boolean isConstraintsViolation() {
        return isConstraintsViolation;
    }

    public void setConstraintsViolation(boolean constraintsViolation) {
        isConstraintsViolation = constraintsViolation;
    }
}
