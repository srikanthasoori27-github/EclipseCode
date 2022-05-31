/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class holding information about a policy constraint.
 * This is the common constraint model for simple policies
 * and newer ones like Entitlement SOD and Custom.
 *
 * Author: Jonathan, Jeff
 *
 * Originally this was developed for simple policies like
 * Account and Risk that don't need a specific Java class to 
 * represent the constraint.  You put all configuration 
 * in an Attributes map using keys that are recognized by the
 * PolicyExecutor.
 *
 * When Entiltlement SOD policies were developed I wanted to 
 * try to build upon the common model rather than extending
 * SODConstraint.  So GenericConstraint now has a List<IdentitySelector>
 * that hold one or more matching rules to be applied to an Identity.
 * These could have gone in the Attribute map but they'll be common enough
 * to make first-class fields.
 *
 * When there is a single IdentitySelector, then this is a simple
 * "matching constraint".  If an identity matches the selector then
 * this policy is considered violated.
 *
 * When there are two IdentitySelectors, then this is considered to
 * be a "mutex constraint" otherwise known as an SOD.  In this case
 * the constraint is considered violated only if the identity 
 * matches both selectors.  While this was developed to model
 * entitlement-level SOD constraints, it is general enough to handle
 * simple role SOD constraints.  For now though, we're going to 
 * continue to model role SOD using the SODConstraint class.
 *
 * In theory there can be more than two IdentitySelectors, the behavior
 * is currently undefined, thouugh SODPolicyExecutor will consider
 * the constraint violated only if all selectors match.
 *
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.List;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLProperty;

/**
 * A class holding information about a policy constraint 
 * used in Entitlement SOD and "advanced" policies.
 */
public class GenericConstraint extends BaseConstraint {
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The matching rules for attribute or entitlement constraints.
     * Unlike the explicit left/right sides used for role constraints
     * this uses a simpler model and just has a list of selectors, expecting
     * two elements in the SOD case. This makes the XML cleaner and
     * provides a way to model simple constraints with only one selector,
     * and compound constraints with more than two selectors if that
     * made sense. Convenience property accessors are provided to 
     * get each "side" for the UI.
     */
    List<IdentitySelector> _selectors;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public GenericConstraint() {
    }

    /**
     * These can have names so they can be referenced in test init files,
     * but they are not guaranteed unique.
     */
    @Override
    public boolean isNameUnique() {
        return false;
    }

    public void load() {
        super.load();
        if (_selectors != null) {
            for (IdentitySelector is : _selectors)
                is.load();
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The matching rules for attribute or entitlement constraints.
     */
    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<IdentitySelector> getSelectors() {
        return _selectors;
    }

    public void setSelectors(List<IdentitySelector> list) {
        // TODO: constraint the list to two elements?
        _selectors = list;
    }
        
    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    public IdentitySelector getSelector(int psn) {
        return (_selectors != null && psn < _selectors.size()) ? 
            _selectors.get(psn) : null;
    }

    public void setSelector(int psn, IdentitySelector sel) {
        if (_selectors == null)
            _selectors = new ArrayList<IdentitySelector>();

        // null fill
        int required = psn + 1;
        while (_selectors.size() < required)
            _selectors.add(null);

        _selectors.set(psn, sel);
    }

    public IdentitySelector getLeftSelector() {
        return getSelector(0);
    }

    public void setLeftSelector(IdentitySelector sel) {
        setSelector(0, sel);
    }

    public IdentitySelector getRightSelector() {
        return getSelector(1);
    }

    public void setRightSelector(IdentitySelector sel) {
        setSelector(1, sel);
    }

}
