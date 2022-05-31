/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class holding various scores and statistics we calculate
 * for Roles.
 *
 * Author: Bernie Margolis
 */

package sailpoint.object;

import java.util.LinkedHashMap;
import java.util.Map;


import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class RoleScorecard extends SailPointObject
{

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////
    

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Associated role.
     */
    private Bundle _role;
    
    // Business role statistics
    private int _members;
    private int _membersWithAdditionalEntitlements;
    private int _membersWithMissingRequiredRoles;
    
    // IT role statistics
    private int _detected;
    private int _detectedAsExceptions;
    
    // Entitlement-based statistics
    private int _provisionedEntitlements;
    private int _permittedEntitlements; 

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public RoleScorecard() {
    }

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitRoleScorecard(this);
    }

    // Note that this is not serialized in the XML, it is set
    // known containment in a <Bundle> element.  This is only
    // here for a Hibernate inverse relationship.  This does however
    // mean that you cannot checkout and edit Scorecard objects individually.

    public Bundle getRole() {
        return _role;
    }

    public void setRole(Bundle role) {
        _role = role;
    }
    
    /** 
     * Number of identities that are assigned this Business role
     */
    @XMLProperty
    public int getMembers() {
        return _members;
    }

    public void setMembers(int _members) {
        this._members = _members;
    }

    /** 
     * Number of identities that are assigned this Business role as well as entitlements 
     * beyond the scope of their assigned roles
     */
    @XMLProperty
    public int getMembersWithAdditionalEntitlements() {
        return _membersWithAdditionalEntitlements;
    }

    public void setMembersWithAdditionalEntitlements(int withAdditionalEntitlements) {
        _membersWithAdditionalEntitlements = withAdditionalEntitlements;
    }

    /** 
     * Number of identities that are assigned this Business role but are not detected to have 
     * the IT roles that it requires
     */
    @XMLProperty
    public int getMembersWithMissingRequiredRoles() {
        return _membersWithMissingRequiredRoles;
    }

    public void setMembersWithMissingRequiredRoles(int withMissingRequiredRoles) {
        _membersWithMissingRequiredRoles = withMissingRequiredRoles;
    }

    /** 
     * Number of identities that are detected to have this IT role
     */
    @XMLProperty
    public int getDetected() {
        return _detected;
    }

    public void setDetected(int _detected) {
        this._detected = _detected;
    }

    /** 
     * Number of identities that are detected to have this IT role but do not have a 
     * Business role that requires or permits it
     */
    @XMLProperty
    public int getDetectedAsExceptions() {
        return _detectedAsExceptions;
    }

    public void setDetectedAsExceptions(int asExceptions) {
        _detectedAsExceptions = asExceptions;
    }

    /** 
     * Number of entitlements that will be provisioned by this role
     */
    @XMLProperty
    public int getProvisionedEntitlements() {
        return _provisionedEntitlements;
    }

    public void setProvisionedEntitlements(int entitlements) {
        _provisionedEntitlements = entitlements;
    }

    /** 
     * Number of entitlements that will be provisioned by the 
     * roles permitted by this one
     */
    @XMLProperty
    public int getPermittedEntitlements() {
        return _permittedEntitlements;
    }

    public void setPermittedEntitlements(int entitlements) {
        _permittedEntitlements = entitlements;
    }
    
    @Override
    public boolean hasName() {
        return false;
    }
    
    /**
     * Override the default display columns for this object type.
     */
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "Id");
        cols.put("role", "Role");
        cols.put("members", "Members");
        cols.put("membersWithAdditionalEntitlements", "Members with Additional Entitlements");
        cols.put("membersWithMissingRequiredRoles", "Members with Missing Required Roles");
        cols.put("detected", "Identities Detected");
        cols.put("detectedAsExceptions", "Identities Detected as Exceptions");
        cols.put("provisionedEntitlements", "Provisioned Entitlements");
        cols.put("permittedEntitlements", "Permitted Entitlements");
        return cols;
    }

    /**
     * Override the default display format for this object type.
     */
    public static String getDisplayFormat() {
        return "%-34s %-20s %s %s %s %s %s %s %s \n";
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////
}
