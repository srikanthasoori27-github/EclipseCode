/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service;

import java.util.Map;

import sailpoint.object.Link;

/**
 * A DTO with information about account action decision and comment
 */
public class AccountAction {

    private Link link;
    private String action;
    private String comment;
    private static final String COMMENT = "comment";
    public static final String ACTION = "action";
    public static final String ACTION_CREATE = "Create";
    public static final String APPLICATION_ID = "applicationId";

    /**
     * Construct a AccountAction Object
     */
    public AccountAction(Link link, Map<String, Object> decision) {
        this.link = link;
        this.action = (String)decision.get(ACTION);
        this.comment = (String)decision.get(COMMENT);
    }

    /**
     * 
     * @return link
     */
    public Link getLink() {
        return link;
    }

    /**
     * 
     * @return action - decision selected on the give link
     */
    public String getAction() {
        return action;
    }

    /**
     * 
     * @param action - decision selected on the give link
     */
    public void setAction(String action) {
        this.action = action;
    }

    /**
     * 
     * @return comment - comment on the decision
     */
    public String getComment() {
        return comment;
    }

    /**
     * 
     * @param comment - comment on the decision
     */
    public void setComment(String comment) {
        this.comment = comment;
    }
}
