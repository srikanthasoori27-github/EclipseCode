package sailpoint.object;


import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Light weight class containing snapshot of RoleAssignment object.
 * Used by IdentitySnapshot.
 */
public class RoleAssignmentSnapshot implements Serializable, NamedSnapshot {

    /**
     * Name of the role referenced by the assignment
     */
    private String name;

    /**
     * List of the RoleTargets defining the role assignment
     */
    private List<RoleTarget> targets;
    
    public RoleAssignmentSnapshot() {
        this.setName(null);
        this.setTargets(null);
    }
    
    public RoleAssignmentSnapshot(String name) {
        this();
        this.setName(name);
    }
    
    public RoleAssignmentSnapshot(String name, List<RoleTarget> targets) {
        this(name);
        this.setTargets(targets);
    }

    public RoleAssignmentSnapshot(RoleAssignment assignment) {
        this();
        if (assignment != null) {
            this.setName(assignment.getRoleName());
            this.setTargets(new ArrayList<RoleTarget>());
            for (RoleTarget target : Util.safeIterable(assignment.getTargets())) {
                this.getTargets().add(new RoleTarget(target));

            }
        }
    }


    /**
     * Name of the role referenced by the assignment
     */
    @XMLProperty
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * List of the RoleTargets defining the role assignment
     */
    @XMLProperty(xmlname="RoleTargets")
    public List<RoleTarget> getTargets() {
        return targets;
    }

    public void setTargets(List<RoleTarget> targets) {
        this.targets = targets;
    }
}