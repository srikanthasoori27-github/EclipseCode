/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author Kelly Grizzle
 */

package sailpoint.object;

import java.util.List;

import sailpoint.tools.GeneralException;


/**
 * This is an interface for an object that maintains
 * a group of entitlements on an application.
 */
public interface Entitlements extends Certifiable {

    /**
     * The name of the application on which these entitlements live.
     * 
     * @return The name of the application on which these entitlements live.
     */
    public String getApplicationName();

    /**
     * Return the Application object represented by this set of entitlements.
     * 
     * @param  resolver  The Resolver that can be used to look up the application.
     * 
     * @return The Application object represented by this set of entitlements.
     */
    public Application getApplicationObject(Resolver resolver)
        throws GeneralException;

    /**
     * @return The instance if for template applications.
     */
    public String getInstance();

    /**
     * Return the native identity of the account on which the entitlements live.
     * 
     * @return The native identity of the account on which the entitlements live.
     */
    public String getNativeIdentity();

    /**
     * Return the display name for the account on which the entitlements live.
     *
     * @return Display name for the account on which the entitlements live.
     */
    public String getDisplayName();

    /**
     * Return the permissions for this set of entitlements.
     * 
     * @return The permissions for this set of entitlements.
     */
    public List<Permission> getPermissions();

    /**
     * Return the attributes for this set of entitlements.
     * 
     * @return The attributes for this set of entitlements.
     */
    public Attributes<String,Object> getAttributes();

    /**
     * Cannot seem to get the list of map Attribute map keys from JSF
     * by using "item.attributes.keys" where "item" is an EntitlementGroup
     * and Attribute.getKeys is defined to convert the keySet into a List<String>.
     * The thought is that as soon as it notices that Attributes is a Map, you
     * cannot call getter methods.  So something will be provided here.
     */
    public List<String> getAttributeNames();

    /**
     * Create a cloned Entitlements that has the given permissions and
     * attributes.
     */
    public Entitlements create(List<Permission> perms,
                               Attributes<String,Object> attrs);
    
    /**
     * Convert this Entitlements into an EntitlementSnapshot.
     * @return An EntitlementSnapshot representing these entitlements.
     */
    public EntitlementSnapshot convertToSnapshot();

    /**
     * Returns true if the entitlement
     * @return True if the entitlement is only the account.
     */
    public boolean isAccountOnly();
}
