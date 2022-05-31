/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Class used to model a summary of an object to be created,
 * or the changes about to be made to an existing object.
 * This is intended for use in approval work item pages
 * where the objects are relatively simple.
 * 
 * Author: Jeff
 *
 * This is conceptually similar to BundleDifference
 * and IdentityDifference but simpler and hopefully
 * useable for several simpler objects.  The first
 * use was for approving the creation and editing
 * of ManagedAttributes.
 *
 */

package sailpoint.object;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

import java.io.Serializable;
import java.util.List;

@XMLClass
public class ChangeSummary extends AbstractXmlObject
    implements Serializable
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * True if this represents the creation of a new object.
     */
    boolean _create;

    /**
     * True if this represents the deletion of a new object.
     */
    boolean _delete;

    /**
     * A flat list of change to be made. When creating
     * new objects this should be the full list of attributes.
     * When editing objects, this should contain only what changed.
     */
    List<Difference> _differences;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Interface
    //
    //////////////////////////////////////////////////////////////////////
    
    public ChangeSummary() {
    }

    @XMLProperty
    public boolean isCreate() {
        return _create;
    }

    public void setCreate(boolean b) {
        _create = b;
    }

    @XMLProperty
    public boolean isDelete() {
        return _delete;
    }

    public void setDelete(boolean b) {
        _delete = b;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<Difference> getDifferences() {
        return _differences;
    }

    public void setDifferences(List<Difference> diffs) {
        _differences = diffs;
    }
    
}
