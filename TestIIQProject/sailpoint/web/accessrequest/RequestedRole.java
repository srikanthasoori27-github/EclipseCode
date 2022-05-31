package sailpoint.web.accessrequest;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.ProvisioningTarget;
import sailpoint.tools.GeneralException;

import java.util.Map;

/**
 * Class used by AccessRequest to represent a requested role
 */
public class RequestedRole extends RequestedAccessItem {
    public static final String PERMITTED_BY_KEY = "permittedById";
    public static final String ASSIGNMENT_NOTE_KEY = "assignmentNote";
    public static final String ASSIGNMENT_ID_KEY = "assignmentId";
    
    private String permittedById;
    private String assignmentNote;
    private String assignmentId;
    
    /**
     * Constructor.  "id" is required to be present in data.
     * @param data Map of properties.  
     * @throws GeneralException If data does not contain "id"
     */
    public RequestedRole(Map<String, Object> data) throws GeneralException {
        super(data);
        if (data != null) {
            permittedById = (String) data.get(PERMITTED_BY_KEY);
            assignmentNote = (String) data.get(ASSIGNMENT_NOTE_KEY);
            assignmentId = (String) data.get(ASSIGNMENT_ID_KEY);
        }
        validateState();
    }

    /**
     * Returns permitting role id or null if not a permitted role
     * @return permitting role id or null if not a permitted role
     */
    public String getPermittedById() {
        return permittedById;
    }

    /**
     * Returns assignment note 
     * @return assignment note or null if no note has been set
     */
    public String getAssignmentNote() {
        return assignmentNote;
    }

    /**
     * Returns assignment id 
     * @return assignment id or null if no id has been set
     */
    public String getAssignmentId() {
        return assignmentId;
    }

    /**
     * Returns the name for the requested role
     * @param context SailPointContext
     * @return Name of role
     * @throws GeneralException
     */
    public String getName(SailPointContext context) throws GeneralException {
        return ObjectUtil.getName(context, Bundle.class, getId());
    }

    public String getPermittedByName(SailPointContext context) throws GeneralException {
        return ObjectUtil.getName(context, Bundle.class, getPermittedById());
    }

    @Override
    protected ProvisioningTarget initializeProvisioningTarget(SailPointContext context) throws GeneralException {
        ProvisioningTarget target = new ProvisioningTarget();
        //PlanCompiler will put permitted roles under the ProvTarget for the requested Bus Role
        target.setRole(getPermittedById() != null ? getPermittedByName(context) : getName(context));
        return target;
    }

}
