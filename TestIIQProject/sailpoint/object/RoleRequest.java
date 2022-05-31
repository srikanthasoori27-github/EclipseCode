/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Used to record information about a permitted role request in an Identity.
 * This has all of the same information as RoleAssignment, and adds
 * some context around the request and is stored in a different
 * preferences attribute.
 *
 * Author: Jeff
 *
 * DEPRECATED!
 *
 * As of 6.3, this model will be upgraded to nested RoleAssignments inside
 * the RoleAssignment for the assigned role that permits this role.
 */

package sailpoint.object;

import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Used to record information about a permitted role request in an Identity.
 * This has all of the same information as RoleAssignment, and adds
 * some context around the request and is stored in a different
 * preferences attribute.
 */
@XMLClass
public class RoleRequest extends RoleAssignment 
{

    //////////////////////////////////////////////////////////////////////
    // 
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final long serialVersionUID = 4800728678308374605L;

    /**
     * The id of the business role that permitted this role.
     */
    String _permittedById;
  
    /**
     * The name of the business role that permitted this role.
     */
    String _permittedByName;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public RoleRequest() {
    }

    /**
     * The id of the assigned role that permitted this requested role.
     */
    @XMLProperty
    public void setPermittedById(String s) {
        _permittedById = s;
    }

    public String getPermittedById() {
        return _permittedById;
    }

    /**
     * The name of the assigned role that permitted this requested role.
     */
    @XMLProperty
    public void setPermittedByName(String s) {
        _permittedByName = s;
    }

    public String getPermittedByName() {
        return _permittedByName;
    }

}
