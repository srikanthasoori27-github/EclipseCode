/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Class used to contain the results of a comparison between two
 * IdentitySnapshot objects.
 * 
 * Author: Jeff
 *
 * I added this because differences can't all be represented as
 * a simple List of Difference objects,  we've got several different
 * representations for different parts of the identity.  It is convenient
 * to generate all of them at once and have an object to pass the
 * collection around.
 *
 * This is not part of the Hibernate model, but it does have XML.
 */

package sailpoint.object;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * Class used to contain the results of a comparison between two
 * IdentitySnapshot objects.
 */
@XMLClass
public class IdentityDifference extends AbstractXmlObject
  implements Serializable
{
    /**
     * Name of the special attribute under which policy violations are stored.
     */
    public static final String ATT_POLICY_VIOLATIONS = "policyViolations";

    //////////////////////////////////////////////////////////////////////  
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////  

    /**
     * List of changes to top-level attributes of the identity.
     * This does not include Link attributes. Bundle and PolicyViolation
     * differences are stored in here with the special attribute names
     * {@link Identity#ATT_BUNDLES} and {@link #ATT_POLICY_VIOLATIONS}.
     */
    List<Difference> _attributeDifferences;

    // NOTE: The main reason we have a different list for the link attributes
    // is because the Difference objects on this list will have a  
    // context set (containing the name of the application).  It is
    // therefore likely that these would be displayed in a different
    // table, though I suppose we could merge these.

    /**
     * List of changes to the entitlement attributes.
     * These are Link attribute used in Profile matching.
     */
    List<Difference> _linkDifferences;

    /**
     * List of changes to Permissions held by the identity.
     * These are a special case of link attributes that require
     * a more complex model to convey the changes.
     */
    List<PermissionDifference> _permissionDifferences;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public IdentityDifference() {
    }

    public boolean hasDifferences() {

        return ((_attributeDifferences != null &&
                 _attributeDifferences.size() > 0) ||

                (_linkDifferences != null &&
                 _linkDifferences.size() > 0) ||

                (_permissionDifferences != null &&
                 _permissionDifferences.size() > 0)
                );
    }

    public void addAttributeDifferences(List<Difference> diffs) {
        if (_attributeDifferences == null)
            _attributeDifferences = diffs;
        else if (null != diffs)
            _attributeDifferences.addAll(diffs);
    }

    public void addAttributeDifference(Difference d) {
        if (_attributeDifferences == null)
            _attributeDifferences = new ArrayList<Difference>();
        _attributeDifferences.add(d);
    }

    public void addBundleDifference(Difference d) {
        d.setAttribute(Identity.ATT_BUNDLES);
        addAttributeDifference(d);
    }
    
    public void addAssignedRoleDifference(Difference d) {
        d.setAttribute(Identity.ATT_ASSIGNED_ROLES);
        addAttributeDifference(d);
    }

    public void addPolicyViolationDifference(Difference d) {
        d.setAttribute(ATT_POLICY_VIOLATIONS);
        addAttributeDifference(d);
    }

    public void addLinkDifferences(List<Difference> diffs) {
        if (_linkDifferences == null)
            _linkDifferences = diffs;
        else if (null != diffs)
            _linkDifferences.addAll(diffs);
    }

    public void addPermissionDifferences(List<PermissionDifference> diffs) {
        if (_permissionDifferences == null)
            _permissionDifferences = diffs;
        else if (null != diffs)
            _permissionDifferences.addAll(diffs);
    }

    public void add(PermissionDifference diff) {
        if (_permissionDifferences == null)
            _permissionDifferences = new ArrayList<PermissionDifference>();
        _permissionDifferences.add(diff);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * List of changes to top-level attributes of the identity.
     * This does not include Link attributes. Bundle and PolicyViolation
     * differences are stored in here with the special attribute names
     * {@link Identity#ATT_BUNDLES} and {@link #ATT_POLICY_VIOLATIONS}.
     */
    @XMLProperty(mode=SerializationMode.LIST)
    public List<Difference> getAttributeDifferences() {
        return _attributeDifferences;
    }

    public void setAttributeDifferences(List<Difference> diffs) {
        _attributeDifferences = diffs;
    }

    /**
     * List of changes to the entitlement attributes.
     * These are Link attribute used in Profile matching.
     */
    @XMLProperty(mode=SerializationMode.LIST)
    public List<Difference> getLinkDifferences() {
        return _linkDifferences;
    }

    public void setLinkDifferences(List<Difference> diffs) {
        _linkDifferences = diffs;
    }

    /**
     * List of changes to Permissions held by the identity.
     * These are a special case of link attributes that require
     * a more complex model to convey the changes.
     */
    @XMLProperty(mode=SerializationMode.LIST)
    public List<PermissionDifference> getPermissionDifferences() {
        return _permissionDifferences;
    }

    public void setPermissionDifferences(List<PermissionDifference> diffs) {
        _permissionDifferences = diffs;
    }


    /**
     * Return the attribute difference for the given attribute name.
     * 
     * @param  attrName  The name of the attribute for which to retrieve the
     *                   Difference.
     * 
     * @return The attribute difference for the given attribute name, or null if
     *         none exists for the requested attribute.
     */
    public Difference getAttributeDifference(String attrName) {
        if ((null != attrName) && (null != _attributeDifferences)) {
            for (Difference diff : _attributeDifferences) {
                if (attrName.equals(diff.getAttribute()))
                    return diff;
            }
        }
        return null;
    }

    /**
     * Return the bundle differences.
     * 
     * @return The bundle differences, or null if there are none.
     */
    public Difference getBundleDifferences() {
        return getAttributeDifference(Identity.ATT_BUNDLES);
    }

    /**
     * Return the assigned role differences
     * @return The assigned role differences, or null if there are none.
     */
    public Difference getAssignedRoleDifferences() {
        return getAttributeDifference(Identity.ATT_ASSIGNED_ROLES);
    }

    /**
     * Return the policy violation differences.
     * 
     * @return The policy violation differences, or null if there are none.
     */
    public Difference getPolicyViolationDifferences() {
        return getAttributeDifference(ATT_POLICY_VIOLATIONS);
    }

    /**
     * Return the link differences for the given application name.
     * 
     * @param  appName  The name of the application for which to retrieve the
     *                  Differences.
     * 
     * @return The link differences for the given application name, or an empty
     *         list if none exist for the requested application.
     */
    public List<Difference> getLinkDifferences(String appName) {
        return getLinkDifferences(appName, null);
    }

    /**
     * Return the link differences for the given application name and native identity.
     *
     * @param appName  The name of the application to match for differences
     * @param nativeIdentity The native identity to match for differences
     *
     * @return The link differences for the given application name, or an empty
     *         list if none exist for the requested application.
     */
    public List<Difference> getLinkDifferences(String appName, String nativeIdentity) {
        List<Difference> diffs = new ArrayList<Difference>();
        String context = generateContext(appName, nativeIdentity);
        if ((null != appName) && (null != _linkDifferences)) {
            for (Difference diff : _linkDifferences) {
                if (context.equals(diff.getContext()))
                    diffs.add(diff);
            }
        }
        return diffs;
    }

    /**
     * Return the permission differences for the given application name.
     * 
     * @param  appName  The name of the application for which to retrieve the
     *                  PermissionDifferences.
     * 
     * @return The permission differences for the given application name, or an
     *         empty list if none exist for the requested application.
     */
    public List<PermissionDifference> getPermissionDifferences(String appName) {
        List<PermissionDifference> diffs = new ArrayList<PermissionDifference>();
        if ((null != appName) && (null != _permissionDifferences)) {
            for (PermissionDifference diff : _permissionDifferences) {
                if (appName.equals(diff.getApplication()))
                    diffs.add(diff);
            }
        }
        return diffs;
    }
    
    public IdentityDifference truncateStrings(int max) throws GeneralException {
        IdentityDifference newDiff = (IdentityDifference)this.deepCopy(null);

        if (newDiff.getAttributeDifferences() != null) {
            ListIterator<Difference> itDiff = newDiff.getAttributeDifferences().listIterator();
            while (itDiff.hasNext()) {
                Difference nextDiff = itDiff.next();
                itDiff.set(nextDiff.truncateStrings(max));
            }
        }
        
        if (newDiff.getLinkDifferences() != null) {
            ListIterator<Difference> itDiff = newDiff.getLinkDifferences().listIterator();
            while (itDiff.hasNext()) {
                Difference nextDiff = itDiff.next();
                itDiff.set(nextDiff.truncateStrings(max));
            }
        }
        
        return newDiff;
    }
    
    public IdentityDifference truncateStrings() throws GeneralException {
        return truncateStrings(Difference.MAX_STRING_LENGTH);
    }

    /**
     * Generate a context string for application and optional native identity
     * @param application Application name
     * @param nativeIdentity Native identity, or null
     * @return String to use for context
     */
    public static String generateContext(String application, String nativeIdentity) {
        String newContext = application;
        if (nativeIdentity != null) {
            newContext = newContext + ":" + nativeIdentity;
        }
        return newContext;
    }
}
