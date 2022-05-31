/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Class used to contain the results of a comparison between two
 * Bundle objects.  These are calculated by the
 * RoleLifecycler and displayed in role approval work items.
 * 
 * Author: Jeff
 *
 */

package sailpoint.object;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


/**
 * Class used to contain the results of a comparison between two
 * Bundle objects.  These are calculated by the
 * RoleLifecycler and displayed in role approval work items.
 */
// !! CRAP ProvisioningBaseline already has a BundleDifference
// we don't use that any more so should change that name but
// need to migrate this, doesn't really matter what this name is, it
// is only saved in transient workflow cases.
// UPDATE: We no longer have ProvisioningBaseline, but have to keep the
// old name in case there are some inside workflow cases.
@XMLClass(xmlname="RoleDifference")
public class BundleDifference extends AbstractXmlObject
  implements Serializable
{
    //////////////////////////////////////////////////////////////////////  
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////  

    //
    // Moved these here from RoleLifecycler for the two convenience accessors
    // Note that these have to have catalog key names
    //

    public static final String ATT_PERMITS = "label_role_permits";
    public static final String ATT_REQUIREMENTS = "label_role_requirements";

    //////////////////////////////////////////////////////////////////////  
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////  

    /**
     * List of changes to simple attributes.
     * These include the name, description, owner, and type.
     * As of 3.0 they include changes to the role relationship
     * lists (inheritance, permits, required) and the extended attributes.
     */
    List<Difference> _attributes;

    /**
     * List of changes to profiles.
     */
    List<ProfileDifference> _profiles;
    
    /**
     * String representation of changes to the IdentitySelector.
     * Should be smarter about this but this will do for now.
     */
    Difference _selector;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public BundleDifference() {
    }

    public boolean hasDifferences() {

        return ((_attributes != null &&
                 _attributes.size() > 0) ||

                (_profiles != null &&
                 _profiles.size() > 0) ||

                (_selector != null)

                );
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty(mode=SerializationMode.LIST)
    public List<Difference> getAttributeDifferences() {
        return _attributes;
    }

    public void setAttributeDifferences(List<Difference> diffs) {
        _attributes = diffs;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<ProfileDifference> getProfileDifferences() {
        return _profiles;
    }

    public void setProfileDifferences(List<ProfileDifference> diffs) {
        _profiles = diffs;
    }

    @XMLProperty
    public Difference getSelectorDifference() {
        return _selector;
    }

    public void setSelectorDifference(Difference diff) {
        _selector = diff;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    public void add(Difference diff) {
        if (diff != null) {
            if (_attributes == null) 
                _attributes = new ArrayList<Difference>();
            _attributes.add(diff);
        }
    }

    public void add(ProfileDifference diff) {
        if (diff != null) {
            if (_profiles == null) 
                _profiles = new ArrayList<ProfileDifference>();
            _profiles.add(diff);
        }
    }

    /**
     * Convenience accessors for workflows.
     */
    public Difference getDifference(String name) {

        Difference found = null;
        if (_attributes != null && name != null) {
            for (Difference d : _attributes) {
                if (name.equals(d.getAttribute())) {
                    found = d;
                    break;
                }
            }
        }
        return found;
    }

    public Difference getPermitsDifference() {
        return getDifference(ATT_PERMITS);
    }

    public Difference getRequirementsDifference() {
        return getDifference(ATT_REQUIREMENTS);
    }

}
