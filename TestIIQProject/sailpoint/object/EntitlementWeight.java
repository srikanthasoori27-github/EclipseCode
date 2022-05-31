/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:bernie.margolis@sailpoint.com">Bernie Margolis</a>
 */

package sailpoint.object;

import java.io.Serializable;

import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * An EntitlementWeight holds the weight value of the specified
 * entitlement category.
 */
@XMLClass
public class EntitlementWeight implements Cloneable, Serializable, Comparable {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    @XMLClass
    public static enum EntitlementType {
        none,
        permission,
        attribute
    }
    
    private EntitlementType _type;
    private String _target;
    private String _value;
    private String _weight;

    private static final long serialVersionUID = 5099513258288797343L;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public EntitlementWeight() {
        _type = EntitlementType.none;
        _target = null;
        _value = null;
        _weight = "0";
    }

    public EntitlementWeight(final EntitlementType type, final String target, final String value, final String weight) {
        _type = type;
        _target = target;
        _value = value;
        _weight = weight;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public void setType(EntitlementType type) {
        _type = type;
    }

    @XMLProperty
    public EntitlementType getType() {
        return _type;
    }

    public void setTarget(String target) {
        _target = target;
    }

    @XMLProperty
    public String getTarget() {
        return _target;
    }

    public void setValue(String value) {
        _value = value;
    }    

    @XMLProperty
    public String getValue() {
        return _value;
    }
    
    public void setWeight(String weight) {
        _weight = weight;
    }

    @XMLProperty
    public String getWeight() {
        return _weight;
    }

    @Override
    public Object clone() {
        return new EntitlementWeight(_type, _target, _value, _weight);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Convenience methods
    //
    //////////////////////////////////////////////////////////////////////

    public boolean isPermission() {
        return (_type == EntitlementType.permission);
    }

    public boolean isAttribute() {
        return (_type == EntitlementType.attribute);
    }

    // when EntitlementType.permission
    public String getRight() {
        return _value;
    }

    // when EntitlementType.attribute
    public String getAttribute() {
        return _target;
    }

    /**
     * Compares the "value" (for example, name) of two entitlement weights and returns the result.  
     * Note that this ignores the weights entirely. It is just intended to determine whether
     * two objects represent the same entitlement.
     */
    public int compareTo(Object o) {
        final int result;
        
        if (o instanceof EntitlementWeight) {
            EntitlementWeight otherWeight = (EntitlementWeight) o;
            if (otherWeight == null) {
                result = -1;
            } else if (getRight() == null) {
                if (otherWeight.getRight() == null) {
                    result = 0;
                } else {
                    result = 1;
                }
            } else if (otherWeight.getRight() == null) {
                result = -1;
            } else {
                result = getRight().compareTo(otherWeight.getRight());
            }
        } else {
            throw new IllegalArgumentException("Can only compare EntitlementWeight instances to other EntitlementWeight instances");
        }
        
        return result;
    }
}
