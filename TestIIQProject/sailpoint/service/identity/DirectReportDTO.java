/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.identity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A DTO with information about a person that reports to a manager, including which actions the
 * manager can perform on the user.
 */
public class DirectReportDTO {

    private String id;
    private String name;
    private String displayName;
    private Map<String, Object> actions;

    /**
     * Construct a DirectReportDTO without any supported actions.
     *
     * @param id  The ID of the direct report Identity.
     * @param name  The name of the direct report Identity.
     * @param displayName  The display name of the direct report.
     */
    public DirectReportDTO(String id, String name, String displayName) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
    }

    /**
     * Return the ID of the direct report Identity.
     */
    public String getId() {
        return this.id;
    }

    /**
     * Return the name of the direct report Identity.
     */
    public String getName() {
        return name;
    }

    /**
     * Return the display name of the direct report Identity.
     */
    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * Return a possible-null Map with the actions (QuickLink.LCM_ACTION_* constants) that the
     * manager can perform on this direct report. The keys in the map are the actions and the values are the
     * corresponding quicklink names for the action.
     */
    public Map<String, Object> getActions() {
        return this.actions;
    }

    /**
     * Add the given supported action.
     */
    public void addAction(String action, List<String> quickLinkNames) {
        if (null == this.actions) {
            this.actions = new HashMap<>();
        }
        this.actions.put(action, quickLinkNames);
    }
}
