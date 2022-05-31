/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * NOTE: This class has been deprecated in 3.0.  It will be converted
 * to the ObjectConfig/ObjectAttribute model during upgrade.
 *
 * To make sure that the 3.0 code isn't using this any more (other than
 * the upgrader) I removed all the convenience methods.
 *
 * Author: Jeff
 */
package sailpoint.object;

import java.util.List;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * @deprecated This class has been deprecated in 3.0. It will be converted
 * to the ObjectConfig/ObjectAttribute model during upgrade.
 */
@XMLClass
public abstract class ExtendedAttributeConfig extends SailPointObject 
    implements Cloneable
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The attributes defined for the identity cube.
     * Could derive a Map search structure from this, but it does not
     * appear to be necessary right now. Aggregator would rather
     * iterate over the collection.
     */
    List<IdentityAttribute> _attributes;

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public void setAttributes(List<IdentityAttribute> atts) {
        _attributes = atts;
    }
    
    public List<IdentityAttribute> getAttributes() {
        return _attributes;
    }

}
