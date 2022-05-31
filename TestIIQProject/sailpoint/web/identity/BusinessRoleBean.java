/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.identity;

import sailpoint.object.Bundle;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.Entitlements;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.tools.GeneralException;

import java.util.List;
import java.util.Map;


/**
 * A bean common interface for a business role assigned to an identity.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public interface BusinessRoleBean {

    /**
     * Return the ID of the business role.
     */
    public String getId();

    /**
     * Return the name of the business role.
     */
    public String getName() throws GeneralException;

    /**
     * Return the description of the business role.
     */
    public String getDescription() throws GeneralException;

    /**
     * Return the Entitlements that grant this business role for the identity.
     */
    public List<? extends Entitlements> getEntitlements() throws GeneralException;

    /**
     * Get the entitlements that grant each role in the hierarchy of this role.
     * This is similar to {@link #getEntitlements()}, but allows determining the
     * entitlements that grant each role in a hierarchy.
     * 
     * @return A List of Entitlements that grant each role in the hierarchy that
     *         of this role.
     */
    public Map<Bundle, List<EntitlementGroup>> getEntitlementsByRole()
        throws GeneralException;

    /**
     * Return the roles granted by the given application, attribute, value
     * tuple.
     * 
     * @param  app         The name of the application.
     * @param  attr        The name of the attribute or permission.
     * @param  val         The attribute value or permission right.
     * @param  permission  Whether the entitlement is a permission.
     * 
     * @return The roles granted by the given application, attribute, value
     *         tuple.
     */
    public List<Bundle> getRolesForEntitlement(String app, String attr,
                                               String val, boolean permission)
        throws GeneralException;

    /**
     * @return True if this is an assigned role
     */
    public boolean isAssigned();

    /**
     * @return true if the identity is missing the requirements for this role.
     */
    public boolean isMissingRequirements()  throws GeneralException;

    /**
     * Returns role type definition for this role
     * @return RoleTypeDefinition, may be null
     */
    public RoleTypeDefinition getRoleTypeDefinition() throws GeneralException;

    /**
     * @return ID of the identity
     */
    public String getIdentityId();
}
