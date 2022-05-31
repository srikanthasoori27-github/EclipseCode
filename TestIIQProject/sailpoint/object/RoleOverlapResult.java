/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Model to represent the results of a role overlap analysis.
 *
 * Author: Jeff
 *
 * Unlike the similarly named RoleMiningResult this is not a SailPointObject.
 * We currently intend to store these as XML within a TaskResult.
 * 
 * Analysis is carried out for a single role comparing it to all other roles.
 * The result is a collection of items representing the overlap statistics
 * for the other roles.  The model is tabular to make it easier to 
 * convert to an Ext table and sort.
 *
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.List;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Represents the results of a role overlap analysis.
 * These will be stored inside the <code>TaskResult</code>
 * for role impact analysis tasks.
 */
@XMLClass
public class RoleOverlapResult extends AbstractXmlObject
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The database id of the role that everything is compared to.
     */
    String _id;

    /**
     * The name of the role that everything is compared to.
     */
    String _name;

    /**
     * Number of roles we analyzed.
     */
    int _rolesExamined;

    /**
     * List of role overlap items.
     * The size of this list might be less than _rolesExamined if
     * we suppressed some that fell under the minimum overlap
     * threshold.
     */
    List<RoleOverlap> _overlaps;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public RoleOverlapResult() {
    }

    public RoleOverlapResult(Bundle role) {
        if (role != null) {
            _id = role.getId();
            _name = role.getName();
        }
    }

    public boolean isNameUnique() {
        return false;
    }

    /**
     * The database id of the role that everything is compared to.
     */
    @XMLProperty 
    public String getid() {
        return _id;
    }

    public void setid(String s) {
        _id = s;
    }

    /**
     * The name of the role the other roles are compared to.
     */
    @XMLProperty 
    public String getName() {
        return _name;
    }

    public void setName(String s) {
        _name = s;
    }

    /**
     * Number of roles analyzed.
     */
    @XMLProperty 
    public int getRolesExamined() {
        return _rolesExamined;
    }

    public void setRolesExamined(int i) {
        _rolesExamined = i;
    }

    public void incRolesExamined() {
        _rolesExamined++;
    }

    /**
     * List of role overlap items.
     * The size of this list might be less than _rolesExamined if
     * we suppressed some that fell under the minimum overlap
     * threshold.
     */
    @XMLProperty(mode=SerializationMode.LIST)
    public List<RoleOverlap> getRoleOverlaps() {
        return _overlaps;
    }

    public void setRoleOverlaps(List<RoleOverlap> overlaps) {
        _overlaps = overlaps;
    }

    // pseudo property for JSF
    public int getRoleOverlapCount() {
        return (_overlaps != null) ? _overlaps.size() : 0;
    }
    
    public void add(RoleOverlap r) {
        if (r != null) {
            if (_overlaps == null)
                _overlaps = new ArrayList<RoleOverlap>();
            _overlaps.add(r);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // RoleOverlap
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Represents the overlap calculations for one pair of roles.
     */
    @XMLClass
    static public class RoleOverlap {

        /**
         * The name of the other role.
         */
        String _name;

        /**
         * Name of the role type.
         * 
         * @ignore
         * We could determine this at runtime, but it adds a lot
         * of IO to the page rendering.
         */
        String _type;

        /**
         * Overlap percentages.
         */
        int _attribute;
        int _assignment;
        int _localAssignment;
        int _provisioning;
        int _localProvisioning;
        int _composite;

        public RoleOverlap() {
        }

        public RoleOverlap(Bundle role) {
            if (role != null) {
                _name = role.getName();
                _type = role.getType();
            }
        }

        /**
         * The name of the other role.
         */
        @XMLProperty
        public String getName() {
            return _name;
        }

        public void setName(String s) {
            _name = s;
        }

        /**
         * Name of the role type.
         */
        @XMLProperty
        public String getType() {
            return _type;
        }

        public void setType(String s) {
            _type = s;
        }

        /**
         * Percentage of overlap in the extended attributes,
         * description, and other non-essential properties.
         */
        @XMLProperty
        public int getAttribute() {
            return _attribute;
        }

        public void setAttribute(int i) {
            _attribute = i;
        }

        /**
         * Percentage of overlap in the assignment rules
         * and detection profiles. This percentage 
         * includes the contributions of inherited roles.
         */
        @XMLProperty
        public int getAssignment() {
            return _assignment;
        }

        public void setAssignment(int i) {
            _assignment = i;
        }

        /**
         * Percentage of overlap in the assignment rules
         * and detection profiles. This percentage
         * only includes things defined directly on the role
         * not things that were inherited.
         */
        @XMLProperty
        public int getLocalAssignment() {
            return _localAssignment;
        }

        public void setLocalAssignment(int i) {
            _localAssignment = i;
        }

        /**
         * Percentage of overlap in the provisioning plans.
         * This includes contributions of inherited roles.
         */
        @XMLProperty
        public int getProvisioning() {
            return _provisioning;
        }

        public void setProvisioning(int i) {
            _provisioning = i;
        }

        /**
         * Percentage of overlap in the provisioning plans.
         * This does not include inherited plans.
         */
        @XMLProperty
        public int getLocalProvisioning() {
            return _localProvisioning;
        }

        public void setLocalProvisioning(int i) {
            _localProvisioning = i;
        }
    }        


}
