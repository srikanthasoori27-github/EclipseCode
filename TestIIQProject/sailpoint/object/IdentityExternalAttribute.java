/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * 
 */

package sailpoint.object;

import sailpoint.tools.xml.XMLClass;

/**
 * An extension of ExternalAttribute that results in 
 * a table for identity attributes that is distinct
 * from extended attribute tables for other classes.
 */
@XMLClass
public class IdentityExternalAttribute extends ExternalAttribute
    implements Cloneable
{
    private static final long serialVersionUID = 3362870191826823612L;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public IdentityExternalAttribute() { }
  
    public IdentityExternalAttribute(String attName,String attValue,
                                     String identityId) {
        super(attName, attValue, identityId);
    }
}
