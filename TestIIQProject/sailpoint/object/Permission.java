/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A representation of a application-specific permission.
 * 
 * Author: Jeff
 */

package sailpoint.object;

import java.io.Serializable;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import sailpoint.tools.Localizable;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.IXmlEqualable;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;

/**
 * A representation of a application-specific permission.
 *
 * Permissions are modeled as a set of "rights" on a "target".
 */
@XMLClass
public class Permission implements Cloneable, Serializable, Comparable<Permission>, IXmlEqualable<Permission>,
        Localizable
{

    //////////////////////////////////////////////////////////////////////
    //
    // Enumerations
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * @ignore
     * A set of constants for the standard right names.
     * This is intended primarily for the unit tests.  Note though
     * that rights are now extensibly defined in the RightConfig object
     * so you can't assume that this is the full set, or in theory that
     * these names are even being used.
     */
    public static final String CREATE = "create";
    public static final String READ = "read";
    public static final String UPDATE = "update";
    public static final String DELETE = "delete";
    public static final String EXECUTE = "execute";
    
    /**
     * Attribute stored in the attribute map and contains the
     * name of the aggregationSource.
     * 
     * As if 6.1 this will be non-null for any Permissions a
     * aggregated from a TargetCollector and will contain
     * the name of the target collector.
     *
     * Permissions that are adorned through account and group 
     * aggregation will not have an aggregation source set.
     */
    public static final String ATT_AGGREGATION_SOURCE = "aggregationSource";

    /**
     * Attribute stored in the attribute map containting the targetHost
     */
    public static final String ATT_TARGET_HOST = "targetHost";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The target object on the application to which this right applies.
     * Examples include a table name, or the OU of a directory object.
     */
    String _target;

    /**
     * A comma separated list of rights.
     * 
     * @ignore
     * Probably want a Set here, but trying to simplify the Hibernate
     * mapping initially.
     * 
     * NOTE: If we decide to make this a collection, it can
     * no longer be a Hibernate composite-element within
     * the parent Profile.  It will have to be broken
     * out into an entity with a primary key.
     *
     * Sigh, now that we've broken out Right into its own class we
     * could reference them here, but these are relatively static
     * so we can continue to implicitly reference them by name
     * in a csv.  Unless we need to do a lot more analysis on 
     * Permissions, this is probably enough.
     * 
     */
    String _rights;

    /**
     * A string that can be used to hold extra information that 
     * might be known about a permission. Something  
     * commonly seen with customers is storing a time period
     * when the permission can be used. This field is not something
     * that will be queryable or that should be put as part of 
     * profile filters.
     */
    String _annotation;

    /**
     * Cached listified rights.
     * 
     * @ignore
     * Building this list happens a lot during role detection so
     * let's try to avoid garbage where we can.
     */
    List<String> _rightsList;

    /**
     * Additional attributes that are present on the Permission.
     * 
     * Added as part of 6.1 to allow the Permission to describe where
     * the permission was aggregated.
     * 
     * These are not shown in the UI tier and are used for operational
     * purposes only.
     *
     */
    Map<String,Object> _attributes;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public Permission() {
    }

    public Permission(String rights, String target) {
        _rights = rights;
        _target = target;
    }

    public Permission(List<String> rights, String target) {
        _rights = Util.listToCsv(rights, true);
        _target = target;
    }
    
    public Permission(Permission src) {
        _rights = src._rights;
        _target = src._target;
    }

    /**
     * A Permission object containing rights, target, and attributes
     * 
     */
    public Permission(List<String> rights, String target, Map<String,Object> attributes) {
        this(rights, target);
        _attributes = attributes;
    }

    public boolean contentEquals(Permission other) {
        
        return this.equals(other);
    }

    
    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The target object on the application to which this right applies.
     * Examples include a table name, or the OU of a directory object.
     */
    @XMLProperty
    public String getTarget() {
        return _target;
    }

    public void setTarget(String att) {
        _target = att;
    }

    /**
     * A comma separated list of rights.  
     */
    @XMLProperty
    public String getRights() {
        return _rights;
    }

    public void setRights(String s) {
        if (s == null) {
            _rights = null;
            _rightsList = null;
        }
        else {
            _rightsList = Util.csvToList(s, true); 
            if ( _rightsList != null ) {
                // Sort these so equals() works
                // jsl - eww, we're not doing this in the constructor
                // this may cause Hibernate diffs
                Collections.sort(_rightsList);
                _rights = Util.listToCsv(_rightsList);
            }
        }
    }

    public void setRights(List<String> l) {
        if (l == null) {
            _rights = null;
            _rightsList = null;
        }
        else {
            // Sort these so equals() works.
            Collections.sort(l);
            _rightsList = l;
            _rights = Util.listToCsv(_rightsList);
        }
    }

    /**
     * A string that can be used to hold extra information that 
     * might be known about a permission. One example is
     * storing a time period when the permission can be used. This property
     * is not queryable and cannot be used in profile filters.
     */
    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getAnnotation() {
        return _annotation;
    }

    public void setAnnotation(String annotation) {
        _annotation = annotation;
    }
    
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Map<String, Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this._attributes = attributes;
    }

    // jsl - why is this an XMLProperty?  it's already in the Attributes map
    // I actually like it better as a top-level property because
    // it reads cleaner, but this will duplicate it for no reason

    @XMLProperty
    public String getAggregationSource() {
        return getStringAttribute(ATT_AGGREGATION_SOURCE);
    }

    public void setAggregationSource(String source) {
        this.setPseudo(ATT_AGGREGATION_SOURCE, source);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Determine whether this permission is a super-set (or has the same rights
     * as) the given permission.
     * 
     * @param  p  The Permission that should be subsumed.
     * 
     * @return True if this permission is a super-set (or has the same rights
     *         as) the given permission.
     */
    public boolean subsumes(Permission p)
    {
        // Must relate to the same target.
        if ((null == _target) || !_target.equals(p.getTarget()))
            return false;

        List<String> thisRights = getRightsList();
        List<String> requiredRights = p.getRightsList();

        return thisRights.containsAll(requiredRights);
    }
    
    /**
     * Convenience access to return the rights as a list of strings.
     */
    public List<String> getRightsList() {

        if (_rightsList == null) {
            if (_rights != null) {
                _rightsList = Util.csvToList(_rights);
            }
        }
        return _rightsList;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Object overrides
    //
    //////////////////////////////////////////////////////////////////////


    @Override
    public Object clone()
    {
        try
        {
            return super.clone();
        }
        catch (CloneNotSupportedException e) { /* Won't happen - we're cloneable */ }
        return null;
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof Permission))
            return false;
        if (this == o)
            return true;

        Permission perm = (Permission) o;
        // Copy and sort rights so equals() works
        List<String> r1 = new ArrayList<String>();
        if (null != getRightsList()) {
            r1.addAll(getRightsList());
            if(null != r1 && !r1.isEmpty()) {
                Collections.sort(r1);
            }
        }
        List<String> r2 = new ArrayList<String>();
        if (null != perm.getRightsList()) {
            r2.addAll(perm.getRightsList());
            if(null != r2 && !r2.isEmpty()) {
                Collections.sort(r2);
            }
        }
        return new EqualsBuilder()
                        .append(getTarget(), perm.getTarget())
                        .append(Util.listToCsv(r1), Util.listToCsv(r2))
                        .isEquals();
    }

    public int compareTo(Permission perm) {
        
        int comparison = getTarget().compareTo(perm.getTarget());
        if (0 == comparison) {
            // Copy and sort rights so compare() works
            List<String> r1 = new ArrayList<String>();
            if (null != getRightsList()) {
                r1.addAll(getRightsList());
            }
            List<String> r2 = new ArrayList<String>();
            if (null != perm.getRightsList()) {
                r2.addAll(perm.getRightsList());
            }
            if (Util.isEmpty(r1)){
                if (Util.isEmpty(r2)){
                    //if they are both empty they are equal
                    return 0;
                }
                //r1 isEmpty so r2 has more rights
                return 1;
            } else if (Util.isEmpty(r2)){
                //r1 is not empty but r2 is 
                return -1;
            }else {
                Collections.sort(r1);
                Collections.sort(r2);
                // Go through the sorted list of rights until we find one that
                // is different.
                for (int i=0; i<r1.size(); i++) {
                    if (i < r2.size()) {
                        String right1 = r1.get(i);
                        String right2 = r2.get(i);
                        comparison = right1.compareTo(right2);
                        if (0 != comparison) {
                            return comparison;
                        }
                    }
                    else {
                        // r1 has more rights than r2, so r2 is "less".
                        return -1;
                    }
                }
            }
        }
        
        return comparison;
    }

    @Override
    public int hashCode()
    {
        return new HashCodeBuilder().append(getTarget()).append(getRights()).toHashCode();
    }

    /**
     * Returns a description of this permission in a localizable message object.
     */
    public Message getMessage(){
        List<String> rightsList = getRightsList();
        return new Message(MessageKeys.PERMISSION_DESC_MESSAGE, rightsList, this._target);
    }

    public String getLocalizedMessage() {
        Message msg = getMessage();
        return msg.getLocalizedMessage(Locale.getDefault(), TimeZone.getDefault());
    }

    public String getLocalizedMessage(Locale locale, TimeZone timezone) {
        Message msg = getMessage();
        return msg.getLocalizedMessage(locale, timezone);
    }

    @Override
    public String toString()
    {
        return this._rights + " on '" + this._target + "'";
    }

    /**
     * Utility to clone a list of Permissions.
     * Used in cases where a collection owned by one object
     * needs to be copied to another, for example from Identity to 
     * an EntitlementSnapshot.
     */
    static public List<Permission> clone(List<Permission> perms) {

        List<Permission> copy = null;
        if (perms != null) {
            copy = new ArrayList<Permission>();
            for (Permission p : perms) {
                if (p != null)
                    copy.add((Permission)p.clone());
            }
        }
        return copy;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return true if the targets match.
     */
    public boolean hasTarget(String name) {
        
        return (name != null && _target != null && _target.equals(name));
    }

    /**
     * Return true if a given right is included in this permission.
     */
    public boolean hasRight(String name) {

        List<String> rights = getRightsList();

        return (name != null && 
                rights != null &&
                rights.contains(name));
    }

    /**
     * Add rights to the list.
     */
    public void addRights(List<String> rights) {
        if (rights != null) {
            List<String> current = getRightsList();
            if (current == null) {
                // make a copy since we can be modifying our list
                current = new ArrayList<String>(rights); 
                setRights(current);
            }
            else {
                for (String right : rights) {
                    if (!current.contains(right))
                        current.add(right);
                }
                setRights(current);
            }
        }
    }

    /**
     * Remove rights from the list.
     */
    public void removeRights(List<String> rights) {
        if (rights != null) {
            List<String> current = getRightsList();
            if (current != null) {
                for (String right : rights) {
                    current.remove(right);
                }
                setRights(current);
            }
        }
    }
    
   public static final Comparator<Permission> SP_PERMISSION_BY_TARGET =
        new Comparator<Permission>() {
            public int compare(Permission p1, Permission p2) {
                Collator collator = Collator.getInstance();
                collator.setStrength(Collator.PRIMARY);
                return collator.compare(p1.getTarget(), p2.getTarget());
            }
        };
        
    public static final Comparator<Permission> SP_PERMISSION_BY_ANNOTATION =
        new Comparator<Permission>() {
        public int compare(Permission p1, Permission p2) {
            Collator collator = Collator.getInstance();
            collator.setStrength(Collator.PRIMARY);
            String p1a = p1.getAnnotation();
            if ( p1a == null ) p1a = "";
            String p2a = p2.getAnnotation();
            if ( p2a == null ) p2a = "";
            return collator.compare(p1a, p2a);
        }
    };
    
    public static final Comparator<Permission> SP_PERMISSION_BY_RIGHT  =
        new Comparator<Permission>() {
        public int compare(Permission p1, Permission p2) {
            Collator collator = Collator.getInstance();
            collator.setStrength(Collator.PRIMARY);
            return collator.compare(p1.getRights(), p2.getRights());
        }
    };
    
    //////////////////////////////////////////////////////////////////////
    //
    // Convenience methods
    //
    //////////////////////////////////////////////////////////////////////
    
    public void setAttribute(String name, Object value) {
        setPseudo(name, value);
    }
    
    public Object getAttribute(String name) {
        return Util.get(_attributes, name);
    }
    
    public String getStringAttribute(String name) {
        return Util.getString(_attributes, name);
    }
    
    protected void setPseudo(String name, Object value) {
        if (value == null) {
            if (_attributes != null)
                _attributes.remove(name);
        }
        else {
            if (_attributes == null)
                _attributes = new Attributes<String,Object>();
            _attributes.put(name, value);
        }
    }
}
