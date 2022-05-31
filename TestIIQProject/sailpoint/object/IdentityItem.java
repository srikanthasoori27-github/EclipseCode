/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * A class used to represent a single item (attribute or entitlement)
 * from an identity.  This is a general purpose model used in several 
 * contexts.  They are used by Matchmaker to keep track of the
 * specific identity attributes that satisified an IdentitySelecotr
 * or other form of matching rule.  They are used in RoleDetection
 * to keep track of the specific entitlements that were used in 
 * the detection of a role.  
 *
 * Author: Jeff
 * 
 * Try not to clutter this up with too much stuff.  It should be lean
 * and mean so we can use it in several places without dragging
 * in dependencies.  This needs to be "archival" so avoid references
 * to SailPointObjects.
 * 
 * Initially I used a list of EntitlementSnapshots but these 
 * were just SOO wordy in XML and I'd like to keep the size down in the
 * places we need to store these.  The hierarchy wasn't accomplishing
 * much and the need to save a "path" and possibly other annotations
 * on each item meant we couldn't just use a simple Map for attributes
 * and would have to extend the Permission model for permissions.
 *
 * This is also another case where the distinction between attribute
 * and permission isn't very interesting and makes it cumbersome
 * to build and analyze.  
 *
 * 
 * So, we have yet another way to represent collections of attributes
 * and permissions.  It is structurally more like 
 * ProvisioningPlan.GenericRequest and I wish we could migrate toward
 * a flat model like this for storage everywhere.
 * 
 */
package sailpoint.object;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * A class used to represent a single item (attribute or entitlement)
 * from an identity. This is a general purpose model used in several 
 * contexts. They are used by Matchmaker to keep track of the
 * specific identity attributes that satisfied an IdentitySelector
 * or other form of matching rule. They are used in RoleDetection
 * to keep track of the specific entitlements that were used in 
 * the detection of a role.  
 */
@XMLClass
public class IdentityItem extends AccountItem {
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final Log log = LogFactory.getLog(IdentityItem.class);

    /**
     * Application id or null for identity-level attributes.
     */
    String _applicationId;

    /**
     * Application name or null for identity-level attributes.
     */
    String _application;

    /**
     * Instance identifier for template applications.
     */
    String _instance;

    /**
     * Native identity of the application account that had this item.
     */
    String _nativeIdentity;

    /**
     * The display name of the account, typically set when
     * _nativeIdentity is a DN.
     */
    String _accountDisplayName;

    /**
     * The name of a role in cases where the item is associated
     * with a role.  
     */
    String _role;

    /**
     * Contains information on which part of the role hierarchy
     * was interested in this item.  Used only when items are
     * generated as a side effect of role assignment/detection.
     * This is normally a transient field given to the UI it
     * should not be persisted because the path can change over time.
     */
    List<Path> _path;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Path
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Path relationships.
     */
    public static final String RELATIONSHIP_INHERITS = "I";
    public static final String RELATIONSHIP_REQUIRES = "R";
    public static final String RELATIONSHIP_PERMITS = "P";


    /**
     * Normally transient objects maintained in a list to represent
     * the path from a role or entitlement involved in an SOD conflict
     * to the role on an identity that indirectly granted the conflicting 
     * thing.
     *
     * The relationship applies to the previous item on the list.
     * For example this path:
     *
     *     A inherits B requires C permits D
     *
     * Would be expressed in path XML as:
     * <pre>
     * {@code
     * <ItemPaths>
     *   <ItemPath role='A'/>
     *   <ItemPath role='B' relationship='I'/>
     *   <ItemPath role='C' relationship='R'/>
     *   <ItemPath role='D' relationship='P'/>
     * </ItemPaths>
     * }
     * </pre>
     */
    @XMLClass(xmlname="ItemPath")
    public static class Path {

        String _role;
        String _relationship;

        public Path() {
        }

        public Path(String role, String relationship) {
            _role = role;
            _relationship = relationship;
        }

        public Path(Bundle role, String relationship) {
            _role = role.getName();
            _relationship = relationship;
        }

        @XMLProperty
        public String getRole() {
            return _role;
        }
        
        public void setRole(String s) {
            _role = s;
        }
    
        @XMLProperty
        public String getRelationship() {
            return _relationship;
        }
        
        public void setRelationship(String s) {
            _relationship = s;
        }

    }

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public IdentityItem() {
    }

    public IdentityItem(Application app, String name, Object value) {
        setApplication(app);
        setValue(name, value);
    }

    public IdentityItem(Link link, String name, Object value) {
        setAccount(link);
        setValue(name, value);
    }

    public IdentityItem(Link link, Permission perm, Object value) {
        setAccount(link);
        setPermission(perm, value);
    }

    public IdentityItem(Permission perm) {
        setPermission(perm);
    }

    /**
     * Interface used to convert between the older EntitlementGroup model
     * and the newer IdentityItem.
     */
    public IdentityItem(EntitlementGroup group, String name, Object value) {
        setAccount(group);
        // EntitlementGroup does not have display names for attribute values
        setValue(name, value);
    }

    public IdentityItem(EntitlementGroup group, Permission perm) {
        setAccount(group);  
        setPermission(perm);
    }

    /**
     * Set the source application.
     */
    public void setApplication(Application app) {
        if (app != null) {
            _applicationId = app.getId();
            _application = app.getName();
        }
    }
    
    /**
     * Set the source account.
     */
    public void setAccount(Link link) {
        if (link != null) {
            setApplication(link.getApplication());
            _instance = link.getInstance();
            _nativeIdentity = link.getNativeIdentity();
            _accountDisplayName = link.getDisplayName();
        }
    }

    /**
     * Set account information from an EntitlementGroup.
     */
    private void setAccount(EntitlementGroup group) {
        if (group != null) {
            setApplication(group.getApplication());
            _instance = group.getInstance();
            _nativeIdentity = group.getNativeIdentity();
            _accountDisplayName = group.getDisplayName();
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public String getApplicationId() {
        return _applicationId;
    }

    public void setApplicationId(String s) {
        _applicationId = s;
    }

    @XMLProperty
    public String getApplication() {
        return _application;
    }

    public void setApplication(String s) {
        _application = s;
    }

    @XMLProperty
    public String getInstance() {
        return _instance;
    }

    public void setInstance(String s) {
        _instance = s;
    }

    @XMLProperty
    public String getNativeIdentity() {
        return _nativeIdentity;
    }

    public void setNativeIdentity(String s) {
        _nativeIdentity = s;
    }

    @XMLProperty
    public String getAccountDisplayName() {
        return _accountDisplayName;
    }

    public void setAccountDisplayName(String s) {
        _accountDisplayName = s;
    }

    @XMLProperty
    public String getRole() {
        return _role;
    }

    public void setRole(String s) {
        _role = s;
    }

    public void setRole(Bundle role) {
        if (role != null)
            _role = role.getName();
    }

    @XMLProperty(xmlname="ItemPaths")
    public List<Path> getPath() {
        return _path;
    }

    public void setPath(List<Path> path) {
        _path = path;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return true if one item is equal to another except for
     * the value. Used to compress and filter duplicates in 
     * a ViolationDetails.
     */
    public boolean isEqual(IdentityItem other) {
 
        return (super.isEqual(other) &&
                equal(_application, other.getApplication()) &&
                equal(_instance, other.getInstance()) &&
                equal(_nativeIdentity, other.getNativeIdentity()) &&
                equal(_role, other.getRole())
                // leave path out, it's hard to compare and really
                // should be irrelevant for this purpose
                //equal(_path, other.getPath())
                );
    }

}
