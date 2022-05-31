/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class holding information about a Segregation of Duty constraint.
 * A collection of these comprises an SOD Policy.
 *
 * Author: Jeff
 *
 * These are only used to model  conflicts between role memberships.
 * There are "left" and a "right" role lists, if an identity has
 * roles on both sides there is a violation.
 *
 * In 3.0 we introduced the notion of "entitlement level" constraints
 * that can raise violations on things other than just role correlation.
 * Rather than extend this model I decided to built upon GenericConstraint
 * in time we might use BaseConstraint for role SOD as well.
 *
 * POSSIBLE FUTURE PROPERTIES:
 *
 * Some things Approva has on their constraints:
 *
 *   Expiration
 *    This is presumably a date after which the constraint no long
 *    has effect.  Not sure what the application of this would be, possibly
 *    there need to be temporary constraints in place during corporate
 *    reorganizations.
 *
 *  Exclusions
 *    A list of users and Approva "roles" to which the constraint does
 *    not apply.  Seems like we will want something like this, though this
 *    might be more convenient to model with a special marker stored on 
 *    each Identity (ConstraintExclusion)?
 *
 * WHAT TO CONSTRAIN?
 *
 * The most basic constraint is a pair of Bundles (names or references)
 * which indiciate that an Identity may not be assigned both Bundles
 * simultaneously.
 *
 * As a convenience, it seems useful to allow multiple values on the
 * "right side", e.g.
 *
 *   finance administrator : hr administrator, shipping clerk
 *
 * The alternative would be to two constraints for each item on the RHS,
 * this can lead to clutter and make the overall policy more difficult to 
 * understand.  Continuing with that theme, we can allow multiple values
 * on both sides:
 *
 *   finance administrator, dba : hr administrator, shipping clerk
 * 
 * This could be the equivalent of these individual constraints:
 * 
 *   finance administrator : hr administrator
 *   finance administrator : shipping clerk
 *                     dba : hr administrator
 *                     dba : shipping clerk
 * 
 * This can result in very consise constraints but if you pile too many
 * things in here, it will be hard to describe in detail what the 
 * constraint is actually checking for.
 *
 * We could also want to interpret the groupings as ANDs rather than ORs.
 * In other words, it is the combination of two or more business roles
 * that will conflict with another combination.  Approva appears to
 * support this, one slide shows "Any" as a link implying that if
 * you click on it it can be changed to "And".
 * 
 * In our model, it might be enough just to roll these entitlement combinations
 * into composite business roles, but that could be confusing.
 * 
 * Q: Do we need to support constraints on *combinations* of business roles?
 *   
 * REDUNDANT CONSTRAINTS
 * 
 * If constraints are managed as distinct entities in the data model, it
 * is possible to have redundant constraints if the "sides" are reversed.
 * We will make no attempt to collapse redundant constraints, though we could
 * report on them in the UI.  
 * 
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.List;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;


/**
 * A class defining a role SOD constraint.
 */
@XMLClass
public class SODConstraint extends BaseConstraint implements Cloneable
{
    private static final long serialVersionUID = -6861994197614097606L;

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Name of the "standard policy" this constraint represents.
     * Might want to help give more recognizable business context to the constraints.
     * To make this useful though, probably need a model for
     * these policy standards so pick lists are available. Otherwise
     * its just a free-form text field like _violationSummary or _description.
     * 
     * @ignore
     * jsl - not sure what's up with this, we don't have any accessors for it
     * so it can't be used.
     */
    String _standardReference;

    /**
     * The left set of roles for role constraints.
     */
    List<Bundle> _leftBundles;

    /**
     * The right set of roles for role constraints.
     */
    List<Bundle> _rightBundles;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public SODConstraint() {
    }

    /**
     * @ignore
     * These may have names so we can reference them in test init files,
     * but they are not guaranteed unique.
     */
    @Override
    public boolean isNameUnique() {
        return false;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The left set of roles for role constraints.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<Bundle> getLeftBundles() {
        return _leftBundles;
    }

    public void setLeftBundles(List<Bundle> bundles) {
        _leftBundles = bundles;
    }
        
    /**
     * The right set of roles for role constraints.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<Bundle> getRightBundles() {
        return _rightBundles;
    }

    public void setRightBundles(List<Bundle> bundles) {
        _rightBundles = bundles;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Convenience methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Generate a brief text description of the constraint.
     * 
     * @ignore
     * This is used by the unit tests so be careful changing it.
     */
    public String getCannonicalSummary() {

        StringBuffer b = new StringBuffer();
        if (_leftBundles != null) {
            for (int i = 0 ; i < _leftBundles.size() ; i++) {
                if (i > 0) b.append(",");
                b.append(_leftBundles.get(i).getName());
            }
            b.append(" ");
        }

        b.append(":");

        if (_rightBundles != null) {
            b.append(" ");
            for (int i = 0 ; i < _rightBundles.size() ; i++) {
                if (i > 0) b.append(",");
                b.append(_rightBundles.get(i).getName());
            }
        }
        return b.toString();
    }

    /**
     * Add a bundle reference to the lhs, filtering duplicates.
     */
    public void addLeft(Bundle b) {
        if (b != null) {
            if (_leftBundles == null)
                _leftBundles = new ArrayList<Bundle>();
            if (!_leftBundles.contains(b))
                _leftBundles.add(b);
        }
    }

    public void removeLeft(Bundle b) {
        if (b != null && _leftBundles != null)
            _leftBundles.remove(b);
    }

    /**
     * Add a bundle reference to the rhs, filtering duplicates.
     */
    public void addRight(Bundle b) {
        if (b != null) {
            if (_rightBundles == null)
                _rightBundles = new ArrayList<Bundle>();
            if (!_rightBundles.contains(b))
                _rightBundles.add(b);
        }
    }

    public void removeRight(Bundle b) {
        if (b != null && _rightBundles != null)
            _rightBundles.remove(b);
    }

    /**
     * @exclude
     * Performance experiment to fully load the object so we can clear the cache.
     */
    public void load() {

        if (getPolicy() != null)
            getPolicy().getName();

        if (_leftBundles != null) {
            for (Bundle b : _leftBundles)
                b.load();
        }

        if (_rightBundles != null) {
            for (Bundle b : _rightBundles)
                b.load();
        }
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = super.hashCode();
        result = PRIME * result + ((getCompensatingControl() == null) ? 0 : getCompensatingControl().hashCode());
        result = PRIME * result + (_disabled ? 1231 : 1237);
        result = PRIME * result + ((_leftBundles == null) ? 0 : _leftBundles.hashCode());
        result = PRIME * result + ((getRemediationAdvice() == null) ? 0 : getRemediationAdvice().hashCode());
        result = PRIME * result + ((_rightBundles == null) ? 0 : _rightBundles.hashCode());
        result = PRIME * result + ((_standardReference == null) ? 0 : _standardReference.hashCode());
        result = PRIME * result + ((_name == null) ? 0 : _name.hashCode());
        result = PRIME * result + getWeight();
        return result;
    }

    /**
     * @ignore
     * !! jsl - I don't think logical equality is correct here.
     * Since names are not unique if someone just happens to import
     * a policy with duplicate constraints Hibernate will fail
     * with the "null id in object passed to replicate" exception.
     * This should be an obscure case, but I'd like to understand why
     * we felt it necessary to do logical equality here in the first place?
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        final SODConstraint other = (SODConstraint) obj;
        if (getCompensatingControl() == null) {
            if (other.getCompensatingControl() != null)
                return false;
        } else if (!getCompensatingControl().equals(other.getCompensatingControl()))
            return false;
        if (_disabled != other._disabled)
            return false;
        if (_leftBundles == null) {
            if (other._leftBundles != null)
                return false;
        } else if (!_leftBundles.equals(other._leftBundles))
            return false;
        if (getPolicy() == null) {
            if (other.getPolicy() != null)
                return false;
        } else if (!getPolicy().equals(other.getPolicy()))
            return false;
        if (getRemediationAdvice() == null) {
            if (other.getRemediationAdvice() != null)
                return false;
        } else if (!getRemediationAdvice().equals(other.getRemediationAdvice()))
            return false;
        if (_rightBundles == null) {
            if (other._rightBundles != null)
                return false;
        } else if (!_rightBundles.equals(other._rightBundles))
            return false;
        if (_standardReference == null) {
            if (other._standardReference != null)
                return false;
        } else if (!_standardReference.equals(other._standardReference))
            return false;
        if (_name == null) {
            if (other.getName() != null)
                return false;
        } else if (!_name.equals(other.getName()))
            return false;
        if (getWeight() != other.getWeight())
            return false;
        return true;
    }


}
