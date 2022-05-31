/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * @author peter.holcomb
 */
package sailpoint.object;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.XMLProperty;

/**
 * Class used to hold statistics about roles.
 */
public class RoleIndex extends GenericIndex {
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A reference back the bundle that is linked to this index
     */
    Bundle _bundle;

    /** 
     * A count of the number of identities that have this role assigned 
     */
    int _assignedCount;
    
    /** 
     * A count of the number of identities that have this role detected
     */
    int _detectedCount;
    
    /** 
     * Indicates that this is a) an it role and b) that it is currently
     * associated with another role either through permits or requirements
     */
    boolean _associatedToRole;
    
    /** 
     * The date that this role was last certified for membership
     */
    Date _lastCertifiedMembership;
    
    /** 
     * The date that this role was last certified for membership
     */
    Date _lastCertifiedComposition;
    
    /** 
     * Date that this role was last assigned
     */
    Date _lastAssigned;
    
    /** 
     * The number of entitlements related to this role and its hierarchy
     */
    int _entitlementCount;
    int _entitlementCountInheritance;

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public RoleIndex() { }
    
    public RoleIndex(Bundle bundle) {
        this._bundle = bundle;
    }

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitRoleIndex(this);
    }
    
    /** 
     * The number of identities that have this role assigned.
     */
    @XMLProperty
    public int getAssignedCount() {
        return _assignedCount;
    }

    public void setAssignedCount(int count) {
        _assignedCount = count;
    }
    
    public Bundle getBundle() {
        return _bundle;
    }

    public void setBundle(Bundle _bundle) {
        this._bundle = _bundle;
    }

    /** 
     * The number of identities that have this role detected.
     */
    @XMLProperty
    public int getDetectedCount() {
        return _detectedCount;
    }

    public void setDetectedCount(int count) {
        _detectedCount = count;
    }

    /** 
     * The number of entitlements related to this role and its hierarchy.
     */
    @XMLProperty
    public int getEntitlementCount() {
        return _entitlementCount;
    }

    public void setEntitlementCount(int count) {
        _entitlementCount = count;
    }

    /** 
     * The date that this role was last certified for composition.
     */
    @XMLProperty
    public Date getLastCertifiedComposition() {
        return _lastCertifiedComposition;
    }

    public void setLastCertifiedComposition(Date certified) {
        _lastCertifiedComposition = certified;
    }
    
    /** 
     * The date that this role was last certified for membership.
     */
    @XMLProperty
    public Date getLastCertifiedMembership() {
        return _lastCertifiedMembership;
    }

    public void setLastCertifiedMembership(Date certified) {
        _lastCertifiedMembership = certified;
    }
    
    @XMLProperty
    public int getEntitlementCountInheritance() {
        return _entitlementCountInheritance;
    }

    public void setEntitlementCountInheritance(int countInheritance) {
        _entitlementCountInheritance = countInheritance;
    }

    /** 
     * Date that this role was last assigned.
     */
    @XMLProperty
    public Date getLastAssigned() {
        return _lastAssigned;
    }

    public void setLastAssigned(Date assigned) {
        _lastAssigned = assigned;
    }

    /** 
     * True if this is a) an IT role and b) that it is currently
     * associated with another role either through permits or requirements.
     */
    @XMLProperty
    public boolean isAssociatedToRole() {
        return _associatedToRole;
    }

    public void setAssociatedToRole(boolean associatedToRole) {
        _associatedToRole = associatedToRole;
    }

    /**
     * Override the default display columns for this object type.
     */
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "Id");
        cols.put("bundle", "Role");
        cols.put("assignedCount", "Assigned Count");
        return cols;
    }

    /**
     * Override the default display format for this object type.
     */
    public static String getDisplayFormat() {
        return "%-34s %-20s %s\n";
    }
}
