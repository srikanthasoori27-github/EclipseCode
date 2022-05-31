/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * An object defining a role type.  These are stored
 * in the ObjectConfig named "Bundle".
 *
 * Author: Bernie, Jeff
 *
 * Note that most flags are designed to be negative, you have
 * to do something explicit to take features away.  This allows
 * for easier migration of roles from one release to the next.
 * 
 * UI developers should use these as guidelines for hiding
 * componentry, but only if the role does not already have 
 * something controlled by these flags.  For example if the
 * "noSupers" flag is on but the role does have super roles, 
 * the UI still needs to show them even though they violate the type.  
 * It can display an error message to indiciate that the type is 
 * violated but it can't hide anything because these will still 
 * be used during correlation.
 *  
 * Though many of these flags will be the same for a given role
 * type resist the temptation to collapse them.  Keep the model
 * flexible enough to handle what we think now may be unuseful
 * combinations until we have more customer experience.
 *
 */

package sailpoint.object;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

import java.util.List;

/**
 * An object defining a role type.  These are stored
 * in the ObjectConfig named "Bundle".
 */
@XMLClass
public class RoleTypeDefinition extends AbstractXmlObject
    implements Cloneable
{

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    private static final long serialVersionUID = -4788057003641290634L;

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Internal canonical name.
     */
    String _name;

    /**
     * Alternate nice name, can be a message key.
     */
    String _displayName;

    /**
     * Potentially long description.
     */
    String _description;

    /**
     * URL for the image that will be used as an icon for this role type 
     * in the tree view
     */
    String _icon;
    
    /**
     * Flag to indicate that roles of this type cannot inherit
     * from other roles. In other words, they are always the topmost
     * roles in a top-down view.
     *
     * It is unclear whether this is useful though you could use
     * it to prevent the top-tier of container roles from being
     * accidentally inherited. Other than for guidance in the UI,
     * it has no meaning in the system.
     */
    boolean _noSupers;

    /**
     * Flag to indicate that roles of this type cannot be inherited
     * by other roles. In other words, they are always the lowest
     * level leaf roles in a top-down view.
     *
     * It is unclear whether this is useful though you could use
     * it to prevent the very specific IT roles from being specialized.
     * Other than for guidance in the UI, it has no meaning in the system.
     * The UI will check this when you create new roles from the
     * popup menu over a role node.  If the role type allows subs
     * the new role will be created a subrole of the selected role.
     */
    boolean _noSubs;

    /**
     * Flag to indicate that roles of this type cannot be 
     * automatically detected (correlated). This is normally
     * off for IT roles and on for business and container roles.
     */
    boolean _noDetection;

    /**
     * Flag to indicate that roles of this type cannot be
     * automatically detected unless they are required or permitted
     * by an identity's assigned roles. This can be used with large
     * role models to prevent always correlating many "entitlement" roles.
     * This is also useful for customers that want anything not assigned
     * to show up as an additional entitlement.
     */
    boolean _noDetectionUnlessAssigned;
    
    /**
     * Flag to indicate that roles should not have have entitlement profiles.
     * This is normally false for IT roles and true for business 
     * or container roles.
     *
     * If _noDetection is true usually _noProfiles is true but you
     * could have _noProfiles false if you wanted this to be a "structural"
     * role that provides inherited profiles to sub roles but is not
     * itself detectable.
     */
    boolean _noProfiles;

    /**
     * Flag indicating that roles of this type cannot be automatically
     * assigned. This is normally true for IT roles and container roles
     * and false for business roles.
     * 
     * It is analogous to the _noDetection flag for IT roles.
     */
    boolean _noAutoAssignment;

    /**
     * Flag indicating that roles of this type should not have an 
     * assignment selector. This is normally true for IT roles and
     * container roles and false for business roles 
     *
     * It is analogous to the _noProfiles flag for IT roles.
     *
     * If _noAutoAssignment is true usually _noAssignmentSelector is
     * also true.  _noAssignmentSelector may be false for container
     * roles that need to pass an inherited selector fragment down
     * to their sub roles but are not themselves assignable.
     */
    boolean _noAssignmentSelector;

    /**
     * Flag indicating that roles of this type may not be manually assigned.
     * This is normally false for business roles and true for IT and
     * container roles.
     *
     * This will usually have the same value as _noAutoAssignment but
     * maybe not?
     */
    boolean _noManualAssignment;

    /**
     * Flag to indicate that roles should not have a permits list.
     * Normally this is true for business roles and false for IT roles.
     * It might be useful to let this be true for container roles.
     *
     * NOTE: This also controls whether the role is allowed
     * to have a requirements list. It seemed reasonable to control
     * both the requirement and permits list in parallel.
     */
    boolean _noPermits;

    /**
     * Flag to indicate that roles should not be allowed on a permits list.
     * This is typically true for business and container roles.
     */
    boolean _notPermittable;

    /**
     * Flag to indicate that roles should not have a requirements list.
     * Normally this is true for business roles and false for IT roles.
     * It might be useful to let this be true for container roles.
     *
     * Normally this is the same as _noPermits but this keeps them
     * distinct just in case. These can be set in parallel in the config
     * editor if they will always be the same.
     */
    boolean _noRequirements;

    /**
     * Flag to indicate that roles should not be allowed on a 
     * requirements list.
     * 
     * @ignore
     * This is probably not useful, if a role can be permitted
     * then it should also be requireable?
     */
    boolean _notRequired;

    /**
     * Flag to indicate that a role should not be allowed to
     * contain IdentityIQ Cube properties like capabilities and rights.
     */
    boolean _noIIQ;

    /**
     * List of rights which give the user the right to
     * manage roles of this type.
     */
    List<SPRight> _rights;
    
    /**
     * List of role attribute names that are not allowed for this type.
     */
    List<String> _disallowedAttributes;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////
    
    public RoleTypeDefinition() {
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Internal canonical name.
     */
    @XMLProperty
    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }

    /**
     * Alternate nice name, can be a message key.
     */
    @XMLProperty
    public String getDisplayName() {
        return _displayName;
    }

    public void setDisplayName(final String displayName) {
        _displayName = displayName;
    }

    /**
     * Potentially long description.
     */
    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getDescription() {
        return _description;
    }

    public void setDescription(String s) {
        _description = s;
    }

    /**
     * URL for the image that will be used as an icon for this role type 
     * in the tree view
     */
    @XMLProperty
    public String getIcon() {
        return _icon;
    }
    
    public void setIcon(final String icon) {
        _icon = icon;
    }

    /**
     * Flag to indicate that roles of this type cannot inherit
     * from other roles. In other words, they are always the topmost
     * roles in a top-down view.
     */
    @XMLProperty
    public boolean isNoSupers() {
        return _noSupers;
    }

    public void setNoSupers(boolean b) {
        _noSupers = b;
    }

    /**
     * Flag to indicate that roles of this type cannot be inherited
     * by other roles. In other words, they are always the lowest
     * level leaf roles in a top-down view.
     */
    @XMLProperty
    public boolean isNoSubs() {
        return _noSubs;
    }

    public void setNoSubs(boolean b) {
        _noSubs = b;
    }

    /**
     * Flag to indicate that roles of this type cannot be 
     * automatically detected (correlated). This is normally
     * off for IT roles and on for business and container roles.
     */
    @XMLProperty
    public boolean isNoDetection() {
        return _noDetection;
    }

    public void setNoDetection(boolean b) {
        _noDetection = b;
    }

    /**
     * Flag to indicate that roles of this type cannot be
     * automatically detected unless they are required or permitted
     * by an identity's assigned roles.
     */
    @XMLProperty
    public boolean isNoDetectionUnlessAssigned() {
        return _noDetectionUnlessAssigned;
    }
    
    public void setNoDetectionUnlessAssigned(boolean b) {
        _noDetectionUnlessAssigned = b;
    }
    
    /**
     * Flag to indicate that roles should not have have entitlement profiles.
     * This is normally false for IT roles and true for business 
     * or container roles.
     *
     * If noDetection is true usually noProfiles is true but you
     * could have _noProfiles false if you wanted this to be a "structural"
     * role that provides inherited profiles to sub roles but is not
     * itself detectable.
     */
    @XMLProperty
    public boolean isNoProfiles() {
        return _noProfiles;
    }

    public void setNoProfiles(boolean b) {
        _noProfiles = b;
    }

    /**
     * Flag indicating that roles of this type cannot be automatically
     * assigned. This is normally true for IT roles and container roles
     * and false for business roles.
     * 
     * It is analogous to the noDetection flag for IT roles.
     */
    @XMLProperty
    public boolean isNoAutoAssignment() {
        return _noAutoAssignment;
    }

    public void setNoAutoAssignment(boolean b) {
        _noAutoAssignment = b;
    }

    /**
     * Flag indicating that roles of this type should not have an 
     * assignment selector. This is normally true for IT roles and
     * container roles and false for business roles 
     *
     * It is analogous to the _noProfiles flag for IT roles.
     *
     * If noAutoAssignment is true usually noAssignmentSelector is
     * also true.  noAssignmentSelector might be false for container
     * roles that need to pass an inherited selector fragment down
     * to their sub roles but are not themselves assignable.
     */
    @XMLProperty
    public boolean isNoAssignmentSelector() {
        return _noAssignmentSelector;
    }

    public void setNoAssignmentSelector(boolean b) {
        _noAssignmentSelector = b;
    }

    /**
     * Flag indicating that roles of this type might not be manually assigned.
     * This is normally false for business roles and true for IT and
     * container roles.
     *
     * This will usually have the same value as noAutoAssignment.
     */
    @XMLProperty
    public boolean isNoManualAssignment() {
        return _noManualAssignment;
    }

    public void setNoManualAssignment(boolean b) {
        _noManualAssignment = b;
    }

    /**
     * Flag to indicate that roles should not have a permits list.
     * Normally this is true for business roles and false for IT roles.
     * It might be useful to let this be true for container roles.
     *
     * NOTE: This also controls whether the role is allowed
     * to have a requirements list. It seemed reasonable to control
     * both the requirement and permits list in parallel.
     */
    @XMLProperty
    public boolean isNoPermits() {
        return _noPermits;
    }

    public void setNoPermits(boolean b) {
        _noPermits = b;
    }

    /**
     * Flag to indicate that roles should not be allowed on a permits list.
     * This is typically true for business and container roles.
     */
    @XMLProperty
    public boolean isNotPermittable() {
        return _notPermittable;
    }

    public void setNotPermittable(boolean b) {
        _notPermittable = b;
    }

    /**
     * Flag to indicate that roles should not have a requirements list.
     * Normally this is true for business roles and false for IT roles.
     * It may be useful to let this be true for container roles.
     *
     * Normally this is the same as noPermits.
     */
    @XMLProperty
    public boolean isNoRequirements() {
        return _noRequirements;
    }

    public void setNoRequirements(boolean b) {
        _noRequirements = b;
    }

    /**
     * Flag to indicate that roles should not be allowed on a 
     * requirements list.
     */
    @XMLProperty
    public boolean isNotRequired() {
        return _notRequired;
    }

    public void setNotRequired(boolean b) {
        _notRequired = b;
    }

     @XMLProperty(xmlname="RequiredRights", mode=SerializationMode.REFERENCE_LIST)
    public List<SPRight> getRights() {
        return _rights;
    }

    public void setRights(List<SPRight> rights) {
        this._rights = rights;
    }
    
    @XMLProperty(mode=SerializationMode.LIST)
    public List<String> getDisallowedAttributes() {
        return _disallowedAttributes;
    }
    
    public void setDisallowedAttributes(List<String> val) {
        _disallowedAttributes = val;
    }

    @XMLProperty
    public boolean isNoIIQ() {
        return _noIIQ;
    }

    public void setNoIIQ(boolean b) {
        _noIIQ = b;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Convenience methods
    //
    //////////////////////////////////////////////////////////////////////

    public String getDisplayableName() {
        String displayableName = getDisplayName();
        if (displayableName == null || displayableName.trim().length() == 0) 
            displayableName = getName();
        return displayableName;
    }

    public boolean isDetectable() {

        return !isNoDetection();
    }

    /**
     * True if the role can be assigned. This is equivalent to 
     * what is normally called a "business" role.
     */
    public boolean isAssignable() {

        return (!isNoAutoAssignment() && !isNoManualAssignment());
    }

    /**
     * True if role can be manually assigned.
     * 
     * @ignore
     * Here for code readability (positive vs negative condition)
     */
    public boolean isManuallyAssignable() {
        return !isNoManualAssignment();
    }

    /**
     * True if this role has the characteristics of what is normally
     * called an "IT" role. This is used to enable the display of some
     * special options in the modeler. 
     * @ignore
     * ... specifically the allowDuplicateAccounts option that was added for Amex
     * I decided to give this a very specific name to make it clear
     * what it does, something like isIT() may mean other things and
     * be used improperly.  What this really means is that the role
     * can be on the required or detected list.
     */
    public boolean isAllowDuplicateAccounts() {
        return !isNotRequired() || !isNotPermittable();
    }
    

};

