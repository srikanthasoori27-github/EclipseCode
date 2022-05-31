/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Class used to contain the results of a comparison between two
 * Profile objects.  These are calculated by the
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
 * Profile objects. This part of the model built for 
 * role impact analysis. They are calculated by the
 * RoleLifecycler and displayed in role approval work items.
 */
@XMLClass
public class ProfileDifference extends AbstractXmlObject
  implements Serializable
{
    //////////////////////////////////////////////////////////////////////  
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////  

    /**
     * Associated application name.
     */
    String _application;

    /**
     * List of changes to simple attributes.
     * As of 3.0 there are not many of these any more, mostly
     * the description.  
     */
    List<Difference> _attributes;

    /**
     * Changes to the permission list.
     */
    List<Difference> _permissions;

    /**
     * For Profiles, changes to the filters.
     * This captures a single difference between the string representations
     * of the two filters. This might want to be evolve into a more
     * syntax-aware structure so where the changes are can be highlighted.
     */
    Difference _filter;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public ProfileDifference() {
    }

    public boolean hasDifferences() {

        return ((_attributes != null &&
                 _attributes.size() > 0) ||

                (_permissions != null &&
                 _permissions.size() > 0) ||
                
                (_filter != null)
                );
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////


    @XMLProperty
    public String getApplication() {
        return _application;
    }

    public void setApplication(String s) {
        _application = s;
    }


    @XMLProperty(mode=SerializationMode.LIST)
    public List<Difference> getAttributeDifferences() {
        return _attributes;
    }

    public void setAttributeDifferences(List<Difference> diffs) {
        _attributes = diffs;
    }

    // !! crap PermissionDifferences is used in IdentityDifference
    // with different content, clean this up!

    @XMLProperty(mode=SerializationMode.LIST,xmlname="ProfilePermissionDifferences")
    public List<Difference> getPermissionDifferences() {
        return _permissions;
    }

    public void setPermissionDifferences(List<Difference> diffs) {
        _permissions = diffs;
    }

    @XMLProperty
    public Difference getFilterDifference() {
        return _filter;
    }

    public void setFilterDifference(Difference diff) {
        _filter = diff;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    public void setApplication(Application app) {
        if (app != null)
            _application = app.getName();
    }

    public void add(Difference diff) {
        if (diff != null) {
            if (_attributes == null) 
                _attributes = new ArrayList<Difference>();
            _attributes.add(diff);
        }
    }


}
