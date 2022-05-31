package sailpoint.web.accessrequest;

import java.util.Map;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Class used by AccessRequest to contain the role id and permitting role id, if any
 */
public class RemovedRole extends RemovedAccessItem {

    private String assignmentId;
    private String roleLocation;
    //Used to determine if we need negative assignment
    private boolean needsNegativeAssignment;

    public static final String ASSIGNMENT_ID_KEY = "assignmentId";
    public static final String ROLE_LOCATION_KEY = "roleLocation";

    public static final String ASSIGNED_LOCATION = "assigned";
    public static final String DETECTED_LOCATION = "detected";

    /**
     * Constructor.  roleId and roleLocation are required properties
     * @param data Map of properties
     * @throws sailpoint.tools.GeneralException If data does not contain roleId or roleLocation
     */
    public RemovedRole(Map<String, Object> data) throws GeneralException {
        super(data);

        if (data != null) {
            assignmentId = (String) data.get(ASSIGNMENT_ID_KEY);
            roleLocation = (String) data.get(ROLE_LOCATION_KEY);
        }
        validateState();
    }

    /**
     *
     * @return the assignment id
     */
    public String getAssignmentId() {
        return assignmentId;
    }

    /**
     *
     * @return the role location
     */
    public String getRoleLocation() {
        return roleLocation;
    }

    /**
     *
     * @return the comment, or null if no comment has been set
     */
    public String getComment() {
        return comment;
    }

    public void setNeedsNegativeAssignment(boolean b) { this.needsNegativeAssignment = b; }

    public boolean getNeedsNegativeAssignment() { return needsNegativeAssignment; }

    /**
     * Verify invariants
     * @throws sailpoint.tools.GeneralException
     */
    private void validateState() throws GeneralException {
        if(Util.isNullOrEmpty(this.id)) {
            throw new GeneralException("id is required");
        }
        if(Util.isNullOrEmpty(roleLocation)) {
            throw new GeneralException("roleLocation is required");
        }
        if (!ASSIGNED_LOCATION.equals(roleLocation) && !DETECTED_LOCATION.equals(roleLocation)) {
            throw new GeneralException("invalid roleLocation " + roleLocation);
        }
    }

}
