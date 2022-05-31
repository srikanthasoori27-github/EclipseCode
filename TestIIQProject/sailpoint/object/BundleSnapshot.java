/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.List;
import java.io.Serializable;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * An object which represents a definition of a Bundle as it
 * applies to a given Identity at a given point in
 * time.
 * <p>
 * This object retains the bundle name and a
 * snapshot of the entitlements that caused this bundle
 * to be correlated to the Identity.
 * <p> 
 * This object is serialized as xml and is a private object of 
 * IdentitySnapshot object.
 */
@XMLClass
public class BundleSnapshot implements Serializable, NamedSnapshot {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The name of the bundle.
     */ 
    private String _name;

    /**
     * The entitlements that caused us to get this Bundles.
     */ 
    private List<EntitlementSnapshot> _entitlements;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public BundleSnapshot() {
        _name = null;
        _entitlements = null;
    }

    public BundleSnapshot(String name) {
        this();
        _name = name;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Returns the name of the bundle.
     */
    @XMLProperty
    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }

    /**
     * A list of EntitlementSnapshot objects representing the
     * entitlements held by the user that caused the role
     * to be detected.
     */
    @XMLProperty(mode=SerializationMode.LIST)
    public List<EntitlementSnapshot> getEntitlements() {
        return _entitlements;
    }

    public void setEntitlements(List<EntitlementSnapshot> entitlements) {
        _entitlements = entitlements;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Indicates that one of the given links were referenced in the entitlements
     * making up the bundle.
     *
     * @param links Links to find
     * @return  True if the link is referenced in one of the bundle's entitlement
     *  snapshots.
     */
    public boolean referencesLink(List<? extends LinkInterface> links){

        if (links==null || links.isEmpty())
            return false;

        for(LinkInterface link : links){
            for(EntitlementSnapshot entitlementSnap : getEntitlements()){
                if (entitlementSnap.referencesLink(link)){
                        return true;
                }
            }
        }

        return false;
    }

}
