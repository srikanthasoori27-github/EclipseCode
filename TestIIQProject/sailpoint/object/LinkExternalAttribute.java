/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Author: Dan
 */

package sailpoint.object;

import sailpoint.tools.xml.XMLClass;

/**
 * Extension of <code>ExternalAttribute</code> to define
 * a table for the storage of multi-valued extended attribute
 * for Link objects.
 */
@XMLClass
public class LinkExternalAttribute extends ExternalAttribute
    implements Cloneable
{
    private static final long serialVersionUID = 3362870191826187462L;

    //////////////////////////////////////////////////////////////////////
    //
    // Constuctors
    //
    //////////////////////////////////////////////////////////////////////

    public LinkExternalAttribute() { }

    public LinkExternalAttribute(String attName, String attValue, String linkId) {
        super(attName, attValue, linkId);
    }
}
