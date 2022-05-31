/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web.util;

import sailpoint.object.Identity;
import sailpoint.object.SailPointObject;

/**
 * @author peter.holcomb
 *
 */
public class IdentityListConverter extends BaseObjectListConverter<Identity> {

    /**
     * 
     */
    public IdentityListConverter() {
        super(Identity.class);
    }

    @Override
    protected String getObjectName(SailPointObject obj) {

        Identity ident = (Identity) obj;
        return ident.getDisplayableName();
    }
}
