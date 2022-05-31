/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.policyviolation;

import java.util.Date;

import sailpoint.object.ApplicationActivity;
import sailpoint.tools.GeneralException;

/**
 * DTO to represent an ApplicationActivity object.
 */
public class ApplicationActivityDTO {

    /**
     * ApplicationActivity Id
     */
    private String id;

    /**
     * Name of the action
     */
    private String action;

    /**
     * Application name
     */
    private String sourceApplication;

    /**
     * Target of the action
     */
    private String target;

    /**
     * Timestamp of the activity
     */
    private Date timeStamp;

    /**
     * User
     */
    private String user;

    /**
     * Constructor
     * @param activity ApplicationActivity details
     * @throws GeneralException
     */
    public ApplicationActivityDTO(ApplicationActivity activity)
        throws GeneralException {

        if (activity == null) {
            throw new GeneralException("Activity is required");
        }
        this.id = activity.getId();
        this.action = activity.getAction().getMessageKey();
        this.sourceApplication = activity.getSourceApplication();
        this.target = activity.getTarget();
        this.timeStamp = activity.getTimeStamp();
        this.user = activity.getUser();
    }

    public String getId() {
        return id;
    }

    public String getUser() {
        return user;
    }

    public String getAction() {
        return action;
    }

    public String getSourceApplication() {
        return sourceApplication;
    }

    public String getTarget() {
        return target;
    }

    public Date getTimeStamp() {
        return timeStamp;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public void setSourceApplication(String sourceApplication) {
        this.sourceApplication = sourceApplication;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public void setTimeStamp(Date timeStamp) {
        this.timeStamp = timeStamp;
    }
}