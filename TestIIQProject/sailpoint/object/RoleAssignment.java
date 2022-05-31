/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Used to record information about role assignment in an Identity.
 * This is similar to AttributeMetadata but different in that
 * we're recording changes to individual values of a single multi-valued
 * attribute.  There may be other things we want here.
 *
 * Author: Jeff
 *
 * UPDATE: This is also now used to record role revocations made during
 * a certification which are like persistent NON-assignments.  Although
 * it is somewhat confusing I decided not to add another class since we
 * basically need all the same stuff.  The boolean property "negative"
 * determines the polarity of the assignment.
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Used to record information about role assignments and revocations.
 * 
 * This is similar to <code>AttributeMetadata</code> but different in that
 * changes are being recorded to individual values of a single multi-valued
 * attribute.
 *
 * A list of these will be stored in the preferences area of
 * the identity cube.
 * 
 * This was re-actored in 6.0 and now extends Assignment
 * 
 * @see sailpoint.object.Assignment
 * @see sailpoint.object.AttributeAssignment
 */
@XMLClass
public class RoleAssignment extends Assignment 
{

    //////////////////////////////////////////////////////////////////////
    // 
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * 
     */
    private static final long serialVersionUID = 4800728678308374605L;

    /**
     * the id of the assigned role.
     */
    String _roleId;
  
    /**
     * The name of the assigned role.
     */
    String _roleName;

    /**
     * Comments added by the user when the assignment was made.
     * This is free form text and intended to convey the reason the
     * role was assigned. This is an issue now that there can
     * be multiple assignments of the same role, comments help 
     * the user and the certifier understand why both assignments
     * exist and how they are different.
     */
    String _comments;

    /**
     * Optional list of request state for permitted roles.
     * Prior to 6.3 another peer list of RoleRequest objects was used for
     * permits.
     */
    List<RoleAssignment> _permits;

    /**
     * The account targets.
     */
    List<RoleTarget> _targets;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public RoleAssignment() {
    }
    
    public RoleAssignment(String id, String name) {
        _roleId = id;
        _roleName = name;
        _date = new Date();
    }

    public RoleAssignment(Bundle b) {
        setRole(b);
        _date = new Date();
    }

    public RoleAssignment(Bundle b, String assigner, String source) {
        setRole(b);
        _assigner = assigner;
        _source = source;
        _date = new Date();
    }

    public RoleAssignment(Bundle b, String assigner, Source source) {
        setRole(b);
        _assigner = assigner;
        _source = source.toString();
        _date = new Date();
    }

    public RoleAssignment(String id, String assigner, String source) {
        _roleId = id;
        _assigner = assigner;
        _source = source;
        _date = new Date();
    }
    
    public RoleAssignment(String id, String assigner, String source, String assignmentId) {
        this(id, assigner, source);
        this._assignmentId = assignmentId;
    }

    public RoleAssignment(String id, String assigner, Source source) {
        _roleId = id;
        _assigner = assigner;
        _source = source.toString();
        _date = new Date();
    }

    public void setRole(Bundle role) {
        if (role != null) {
            _roleId = role.getId();
            _roleName = role.getName();
        }
    }

    @XMLProperty
    public void setRoleId(String s) {
        _roleId = s;
    }

    /**
     * The id of the assigned role.
     */
    public String getRoleId() {
        return _roleId;
    }

    @XMLProperty
    public void setRoleName(String s) {
        _roleName = s;
    }

    /**
     * The name of the assigned role.
     */
    public String getRoleName() {
        return _roleName;
    }
    
    /**
     * Gets the targets for the role assignment.
     * @return The targets.
     */
    public List<RoleTarget> getTargets() {
        return _targets;
    }

    /**
     * Sets the targets for the role assignment.
     * @param targets The targets.
     */
    @XMLProperty(mode= SerializationMode.INLINE_LIST_UNQUALIFIED)
    public void setTargets(List<RoleTarget> targets) {
        _targets = targets;
    }

    /**
     * Optional comments that can be added by the assigner to explain
     * the purpose of this assignment.
     */
    @XMLProperty
    public void setComments(String s) {
        _comments = s;
    }

    public String getComments() {
        return _comments;
    }

    /**
     * @ignore
     * Normally I'd make these INLINE and avoid a wrapper but I think
     * it's helpful to see that these represent Permits.
     */
    @XMLProperty
    public List<RoleAssignment> getPermittedRoleAssignments() {
        return _permits;
    }

    public void setPermittedRoleAssignments(List<RoleAssignment> permits) {
        _permits = permits;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Permitted Roles
    //
    //////////////////////////////////////////////////////////////////////

    public void addPermittedRole(RoleAssignment ra) {
        if (ra != null) {
            if (_permits == null)
                _permits = new ArrayList<RoleAssignment>();
            _permits.add(ra);
        }
    }

    public void removePermittedRole(RoleAssignment ra) {
        if (ra != null) {
            if (_permits != null)
                _permits.remove(ra);
        }
    }

    /**
     * Look up a permit assignment by role. Duplicate
     * permits within the same role assignment are not allowed.
     */
    public RoleAssignment getPermittedRole(Bundle role) {

        return getPermittedRole(role.getId(), role.getName());
    }

    /**
     * Look up a permit assignment by role. Duplicate
     * permits within the same role assignment are not allowed.
     */
    public RoleAssignment getPermittedRole(String id, String name) {

        RoleAssignment found = null;

        for (RoleAssignment permit : Util.safeIterable(_permits)) {

            if (id != null && id.equals(permit.getRoleId())) {
                // Reset name in case a role was renamed - bug 7903
                if (name != null) {
                    permit.setRoleName(name);
                    found = permit;
                    break;
                }
            }
            else if (name != null && name.equals(permit.getRoleName())) {
                // only do this for the unit tests that don't have ids
                // if there is an id it must be obeyed regardless of name
                found = permit;
                break;
            }
        }
        return found;
    }

    /**
     * Return true if this was a promoted soft permit.
     */
    public boolean isPromotedSoftPermit() {
        return (_assigner == null || _assigner.equals(ASSIGNER_SYSTEM));
    }

    /**
     * Return true if this assignment is for a future date
     */
    public boolean isFutureAssignment() {
        Date start = getStartDate();
        Date now = new Date();
        return (start != null && start.after(now));
    }

    //////////////////////////////////////////////////////////////////////
    //
    // XML Upgrades
    //
    // Convert "id" to "roleId"
    // Convert "name" to "roleName"
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @deprecated use {@link #setRoleId(String)}
     */
    @Deprecated
    @XMLProperty(xmlname="id")
    public void setXmlId(String s) {
        _roleId = s;
    }

    /**
     * @deprecated use {@link #getRoleId()}
     */
    @Deprecated
    public String getXmlId() {
        return null;
    }

    /**
     * @deprecated use {@link #setRoleName(String)}
     */
    @Deprecated
    @XMLProperty(xmlname="name")
    public void setXmlName(String s) {
        _roleName = s;
    }

    /**
     * @deprecated use {@link #getRoleName()}
     */
    @Deprecated
    public String getXmlName() {
        return null;
    }

    /**
     * Old name for roleId. Supported for API backward compatibility
     * but the XML will be auto-upgraded.
     * @deprecated use {@link #setRoleId(String)}
     */
    @Deprecated
    public void setId(String s) {
        setRoleId(s);
    }

    /**
     * @deprecated use {@link #getRoleId()}
     */
    @Deprecated
    public String getId() {
        return getRoleId();
    }

    /**
     * Old name for roleName. Supported for API backward compatibility
     * but the XML will be auto-upgraded.
     * @deprecated use {@link #setRoleName(String)}
     */
    @Deprecated
    public void setName(String s) {
        setRoleName(s);
    }

    /**
     * @deprecated use {@link #getRoleName()}
     */
    @Deprecated
    public String getName() {
        return getRoleName();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Fetch the role object for this assignment.
     * Since unit tests are allowed to create RoleAssignments without
     * ids this needs to be done in a number of places.
     */
    public Bundle getRoleObject(Resolver r) throws GeneralException {

        // if we have an id that takes priority, otherwise fall back to name
        Bundle role = null;
        if (_roleId != null)
            role = r.getObjectById(Bundle.class, _roleId);
        else 
            role = r.getObjectByName(Bundle.class, _roleName);

        return role;
    }

    public void addRoleTarget(RoleTarget target) {
        if (target != null) {
            if (_targets == null)
                _targets = new ArrayList<RoleTarget>();
            _targets.add(target);
        }
    }

    public boolean hasMatchingRoleTarget(RoleTarget roleTarget) {
        for (RoleTarget existingTarget : Util.iterate(_targets)) {
            if (existingTarget.isMatch(roleTarget, true)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Look up a RoleAssignment on a list by assignment id.
     */
    static public RoleAssignment getRoleAssignment(List<RoleAssignment> list, String id) {
        RoleAssignment found = null;
        if (id != null) {
            for (RoleAssignment ra : Util.iterate(list)) {
                if (id.equals(ra.getAssignmentId())) {
                    found = ra;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Initialize a RoleAssignment from a RoleRequest.
     * This is a temporary conversion utility for the 6.3 upgrade process.
     * IdentityIQ will take ownership of some of the RoleRequest which is expected
     * to be destroyed after this call.
     */
    public void convert(RoleRequest src) {
        // Assignment
        setAssignmentId(src.getAssignmentId());
        setAssigner(src.getAssigner());
        setDate(src.getDate());
        setSource(src.getSource());
        setNegative(src.isNegative());
        setStartDate(src.getStartDate());
        setEndDate(src.getEndDate());
        // RoleAssignment
        setRoleId(src.getRoleId());
        setRoleName(src.getRoleName());
        setComments(src.getComments());
        // in theory we should be cloning the permits list too
        // but this constructor is only used to upgrade RoleRequests
        // which won't have a permits list
        setTargets(src.getTargets());
    }

}
