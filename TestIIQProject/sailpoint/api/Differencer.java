/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Utility to compare various objects and generate a summary
 * of the differences.  Used by the Aggregator to determine
 * when to generate identity snapshots, and by the Certificationer
 * to generate change lists for the certification report.
 * 
 * Author: Jeff
 * 
 * UPDATE: Since the Certificationer needs a rather specialized form 
 * of differencing, there is no logic specific to the Certificationer
 * here.  If we don't end up with anything used beyond the Aggregator,
 * consider merging this into Aggregator.
 *
 * A lot of the logic could be moved over to the classes we're
 * comparing, but I like keeping excess business logic
 * off the SailPointObjects.  Then in theory it would be easier
 * to evolve to configurable Differencer implementations.
 * It is also unclear how much of the comparison logic is specific
 * to certain contexts such as diffing to determine whether to 
 * make a new IdentitySnapshot, diffing to generate a displayable
 * list for the Certification report, etc.
 *
 * Obviously this is brute force, it would be snazzy to try to 
 * drive this from reflection and maybe annotations, but unlike
 * XML serialization we don't have that many objects that 
 * need deep differencing.
 * 
 *
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.connector.Connector;
import sailpoint.object.AccountItem;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.BundleSnapshot;
import sailpoint.object.Difference;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.IdentityDifference;
import sailpoint.object.IdentitySnapshot;
import sailpoint.object.LinkSnapshot;
import sailpoint.object.NamedSnapshot;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Permission;
import sailpoint.object.PermissionDifference;
import sailpoint.object.PolicyViolation;
import sailpoint.object.RoleAssignmentSnapshot;
import sailpoint.object.RoleTarget;
import sailpoint.object.SailPointObject;
import sailpoint.object.Scorecard;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.Util.ListFilter;

/**
 * Utility class to compare various objects and generate a summary
 * of the differences. Used by the Aggregator to determine
 * when to generate identity snapshots, and can be of general use
 * in custom workflows.
 *
 * There are two sets of methods in here, those that calculate
 * Difference objects that describe the differences between
 * two objects, and those that return a boolean value to indicate
 * whether there are any differences between two objects.
 *
 * The later methods are used in places where 
 * the generation of something (like an IdentitySnapshot) needs 
 * to be triggered but all the specific differences do not matter.
 *
 * @ignore
 * You could implement the later with the former but its wasteful
 * to build up a potentially complicated list of *all* differences
 * just to throw it away and return true.
 */
public class Differencer
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    static private Log log = LogFactory.getLog(Certificationer.class);

    /**
     * The context providing persistence services.
     */
    SailPointContext _context;

    /**
     * A cache of LinkSnapshot attributes to ignore
     * during differencing.
     */
    List _linkExclusions;

    /**
     * Flag to enable the inclusion of the native account id
     * in the "context" for the difference when comparing two
     * LinkSnapshot objects. This was added for a custom analysis
     * task, it is not used in the core system.
     */
    boolean _includeNativeIdentity;
    
    /** 
     * Flag to signal whether to truncate strings in the Difference objects
     */
    int _maxStringLength;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Constructor.
     */
    public Differencer(SailPointContext con) {
        _context = con;
    }
    
    /**
     * Set to true to include the link's native identity in the context
     * of a Difference when diffing two links. Defaults to false.
     */
    public void setIncludeNativeIdentity(boolean b) {
        _includeNativeIdentity = b;
    }
    
    /**
     * Set max string length for Differences. Use zero 
     * to keep full strings.
     * 
     * @param maxStringLength  The max string length for Differences.
     */
    public void setMaxStringLength(int maxStringLength) {
        _maxStringLength = maxStringLength;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Generic Comparison Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return true if two strings are equal, dealing with nulls.
     */
    static public boolean equal(String s1, String s2) {

        boolean eq = false;
        if (s1 == null)
            eq = (s2 == null);
        else if (s2 != null)
            eq = s1.equals(s2);

        return eq;
    }

    /**
     * Return true if two Dates are equal, dealing with nulls.
     */
    static public boolean equal(Date d1, Date d2) {

        boolean eq = false;
        if (d1 == null)
            eq = (d2 == null);
        else if (d2 != null)
            eq = d1.equals(d2);

        return eq;
    }

    /**
     * Return true if two SailPointObjects are the same.
     * This can be used only for id comparison of already persistent 
     * objects.
     */
    static public boolean equal(SailPointObject o1, SailPointObject o2) {
        boolean eq = false;
        if (o1 == null) {
            eq = (o2 == null);
        }
        else if (o2 != null) {
            eq = (o1 == o2);
            if (!eq)
                eq = equal(o1.getId(), o2.getId());
        }
        return eq;
    }

    /**
     * Return true if two collections are equal.
     * Elements are compared using conatins() which in turn
     * uses equals(). Order of elements is not significant.
     * 
     * Note that null and an empty list are considered equal. 
     * It is hard to keep the various UI beans and internal 
     * components from collapsing empty lists to null.
     *
     * @ignore
     * TODO: Obviously not very effecient for long lists
     * check a threshold and use a Map.
     */
    static public boolean equal(Collection list1, Collection list2) {

        boolean eq = false;

        if (list1 == null) {
            if (list2 == null || list2.isEmpty())
                eq = true;
        }
        else if (list2 == null) {
            eq = list1.isEmpty();
        }
        else if (list1.size() == list2.size()) {
            eq = true;
            Iterator it = list1.iterator();
            while (it.hasNext() && eq) {
                eq = list2.contains(it.next());
            }
        }

        return eq;
    }

    /**
     * Return true if two collections are equal.
     * Uses a supplied <code>Comparator</code> to do 
     * the comparison. Order of elements is not significant. 
     * 
     * Note that null and an empty list are considered equal. 
     * It is hard to keep the various UI beans and internal 
     * components from collapsing empty lists to null.
     */
    static public boolean equal(List l1, List l2, Comparator c) {

        boolean eq = false;

        if (l1 == null) {
            if ((l2 == null) || l2.isEmpty())
                eq = true;
        }
        else if (l2 == null) {
            eq = l1.isEmpty();
        }
        else if (l2.size() == l1.size()) {
            eq = true;
            Iterator it = l1.iterator();
            while (it.hasNext() && eq) {
                eq = contains(l2, it.next(), c);
            }
        }

        return eq;
    }

    /**
     * Return true if an object is contained in a list using
     * the given Comparator rather than the equals method.
     */
    static public boolean contains(List l, Object el, Comparator c) {

        boolean contained = false;
        if (l != null) {
            Iterator it = l.iterator();
            while (it.hasNext() && !contained) {
                Object o = it.next();
                // take the burden off every Comparator to handle nulls
                if (el == null)
                    contained = (o == null);
                else if (o != null && c.compare(el, o) == 0)
                    contained = true;
            }
        }
        return contained;
    }

    /**
     * Return true if two objects are equal. Will forward
     * to {@link #equal(Collection,Collection)} if the arguments
     * are collections.
     * 
     * When called with Strings "" and null they are returned
     * not equal.
     *
     * @see #objectsEqual(Object, Object, boolean)
     *
     * @ignore
     * Used internally for change triggers of extended attributes.  
     * Normally the arguments will be of the same type but because 
     * they can be rule generated we can't ensure that.
     * 
     * !! jsl- revisit this, where in the product is it important
     * to have null and empty be not equal?  This is inconsistent.
     */
    static public boolean objectsEqual(Object o1, Object o2) {
        return objectsEqual(o1, o2, false);
    }
    
    /**
     * Return true if two objects are equal. Will forward
     * to {@link #equal(Collection,Collection)} if the arguments
     * are collections.
     *
     * Our XML Map objects will nullify any "" values
     * when they have made a round-trip to/from the db.
     * 
     * @param o1  The first object to compare.
     * @param o2  The second object to compare.
     * @param nullAndEmptyStrEqual  Whether to treat null and empty
     *    string as equal.
     *
     * @return True if the objects are equal.
     */
    @SuppressWarnings("rawtypes")
    static public boolean objectsEqual(Object o1, Object o2, boolean nullAndEmptyStrEqual) {
        boolean eq = false;
        
        if (o1 == o2)
            eq = true;

        else if (o1 == null) {
            eq = checkNullEquality(o2, nullAndEmptyStrEqual );
        }
        else if (o2 == null) {
            eq = checkNullEquality(o1, nullAndEmptyStrEqual );
        }
        else {
            // TODO: think about the logical equality of an atomic
            // value and list of one element of the same value
            if (o1 instanceof Collection && o2 instanceof Collection)
                eq = equal((Collection)o1, (Collection)o2);
            else
                eq = o1.equals(o2);
        }
        return eq;
    }
    
    
    /**
     * Return true if two objects are equal or else if either one is a
     * collection, return true if the other is a member of that collection.
     * Return false otherwise. This is useful if you want to find out if an
     * element is equal to or part of the value of an attribute.
     * 
     * @param o1  The first object to compare.
     * @param o2  The second object to compare.
     * @param nullAndEmptyStrEqual  Whether null and empty string should
     *    treated as equal.
     *
     * @return True if two objects are equal or if either one is a collection,
     * return true if the other is a member of that collection. False otherwise.
     */
    static public boolean objectsEqualOrContains(Object o1, Object o2, boolean nullAndEmptyStrEqual) {
        
        boolean eq = false;
        
        if (o1 == o2)
            return true;
        
        if (o1 == null || o2 == null || areCompatibleClasses(o1.getClass(), o2.getClass())) {
            eq = objectsEqual(o1, o2, nullAndEmptyStrEqual);
        } else {
            // if either is a collection, check to see that one contains the other.
            if (o1 instanceof Collection) {
                eq = ((Collection) o1).contains(o2);
            }
            
            if (!eq && o2 instanceof Collection) {
                eq = ((Collection) o2).contains(o1);
            }
        }
        
        return eq;
    }
    
    
    /**
     * Check the passed in object for null equality.
     * 
     * Historically, Differencer has always considered empty lists and 
     * null the same thing. With strings, it treated null != "".
     * 
     * The flag was added to keep backward compatibility. 
     * 
     * @param object
     * @param nullAndEmptyStrEqual
     * @return If the object is null or empty.
     */
    @SuppressWarnings("rawtypes")
    private static boolean checkNullEquality(Object object, boolean nullAndEmptyStrEqual) {        
        if ( object == null ) {
            return true;    
        } else
        if ( object instanceof Collection && ((Collection)object).isEmpty() ) {
            return true;
        } else
        if ( object instanceof String && nullAndEmptyStrEqual ) {
            if ( Util.getString((String)object) == null ) 
                return true;                    
        }     
        return false;
    }
    
    private static boolean areCompatibleClasses(Class<?> class1, Class<?> class2) {
        
        boolean compatible = false;

        if (class1 == class2) {
            compatible = true;
        } else if (class1.isAssignableFrom(class2)) {
            compatible = true;
        } else if (class2.isAssignableFrom(class1)) {
            compatible = true;
        }
        
        return compatible;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // BaseComparator
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Common comparator implementation shared by the class-specific
     * comparators.  Subclass this and implement the areEqual() method.
     */
    public abstract static class BaseComparator<T> implements Comparator<T> {

        /**
         * Return whether this Comparator is equal to the given object.
         */
        public boolean equals(Object o) {
            return (o == this);
        }

        /**
         * Compare the given objects by delegating to
         * {@link #areEqual(Object, Object)}.
         */
        public int compare(T o1, T o2) {

            int value = 0;
            if (!areEqual(o1, o2)) {
                // we're not implementing meaningful sort order, just equality
                //value = ((long)snap1 < (long)snap2) ? -1 : 1;
                value = -1;
            }
            return value;
        }

        /**
         * Return whether the given objects are equal.
         */
        public abstract boolean areEqual(T o1, T o2);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // IdentitySnapshot
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Deep equality comparison for IdentitySnapshots.
     */
    public static class IdentitySnapshotComparator 
        extends BaseComparator<IdentitySnapshot> {

        public boolean areEqual(IdentitySnapshot snap1, IdentitySnapshot snap2) {

            boolean eq = false;

            eq = equal(snap1.getApplications(), snap2.getApplications());

            if (eq) {
                Comparator c = new BundleSnapshotComparator();
                eq = equal(snap1.getBundles(), snap2.getBundles(), c);
                if (eq) {
                    c = new RoleAssignmentSnapshotComparator();
                    eq = equal(snap1.getAssignedRoles(), snap2.getAssignedRoles(), c);
                }
            }

            if (eq) {
                Comparator c = new LinkSnapshotComparator();
                eq = equal(snap1.getLinks(), snap2.getLinks(), c);
            }

            if (eq) {
                Comparator c = new EntitlementSnapshotComparator();
                eq = equal(snap1.getExceptions(), snap2.getExceptions(), c);
            }

            // !! unclear if we want to include this in the differences?
            if (eq) {
                Comparator c = new ScorecardComparator();
                eq = (c.compare(snap1.getScorecard(), snap2.getScorecard()) == 0);
            }

            if(eq) {
                Comparator c = new PolicyViolationSnapshotComparator();
                eq = equal(snap1.getViolations(), snap2.getViolations(), c);
            }

            if (eq) {
                // assume these have simple values
                eq = Difference.equal(snap1.getAttributes(), snap2.getAttributes());
            }

            return eq;
        }

    }

    /**
     * Return true if there are any differences between the given
     * IdentitySnapshots.
     *
     * @ignore
     * Used by the Aggregator to see if we should create a new
     * IdentitySnapshot.  
     */
    public boolean equal(IdentitySnapshot snap1, IdentitySnapshot snap2) {

        IdentitySnapshotComparator c = new IdentitySnapshotComparator();
        return c.areEqual(snap1, snap2);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // BundleSnapshot
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Deep equality comparison for BundleSnapshots.
     */
    public static class BundleSnapshotComparator 
        extends BaseComparator<BundleSnapshot> {

        public boolean areEqual(BundleSnapshot snap1, BundleSnapshot snap2) {

            boolean eq = false;

            eq = equal(snap1.getName(), snap2.getName());

            if (eq) {
                Comparator c = new EntitlementSnapshotComparator();
                eq = equal(snap1.getEntitlements(), snap2.getEntitlements(), c);
            }

            return eq;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // LinkSnapshot
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Deep equality comparison for LinkSnapshots.
     */
    public static class LinkSnapshotComparator 
        extends BaseComparator<LinkSnapshot> {

        public boolean areEqual(LinkSnapshot snap1, LinkSnapshot snap2) {

            boolean eq = false;

            eq = equal(snap1.getApplication(), snap2.getApplication());

            if (eq)
                eq = equal(snap1.getSimpleIdentity(), snap2.getSimpleIdentity());

            if (eq)
                eq = equal(snap1.getNativeIdentity(), snap2.getNativeIdentity());

            if (eq) {
                // assume these have simple values
                eq = Difference.equal(snap1.getAttributes(), snap2.getAttributes());
            }

            return eq;
        }

    }

    //////////////////////////////////////////////////////////////////////
    //
    // EntitlementSnapshot
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Deep equality comparison for EntitlementSnapshots.
     */
    public static class EntitlementSnapshotComparator 
        extends BaseComparator<EntitlementSnapshot> {

        public boolean areEqual(EntitlementSnapshot snap1, 
                                EntitlementSnapshot snap2) {

            boolean eq = false;

            eq = equal(snap1.getApplication(), snap2.getApplication());

            if (eq) {
                Comparator c = new PermissionComparator();
                eq = equal(snap1.getPermissions(), snap2.getPermissions(), c);
            }

            if (eq) {
                // assume these have simple values
                eq = Difference.equal(snap1.getAttributes(), snap2.getAttributes());
            }

            return eq;
        }

    }

    //////////////////////////////////////////////////////////////////////
    //
    // EntitlementGroup
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Deep equality comparison for EntitlementGroups.
     */
    public static class EntitlementGroupComparator 
        extends BaseComparator<EntitlementGroup> {

        public boolean areEqual(EntitlementGroup group1, EntitlementGroup group2) {

            boolean eq = false;

            eq = areAppsEqual(group1, group2);

            if (eq) {
                Comparator c = new PermissionComparator();
                eq = equal(group1.getPermissions(), group2.getPermissions(), c);
            }

            if (eq) {
                // assume these have simple values
                eq = Difference.equal(group1.getAttributes(), group2.getAttributes());
            }
            
            if (eq) {
                // check for native identity
                eq = Util.nullSafeEq(group1.getNativeIdentity(), group2.getNativeIdentity(), true);
            }

            return eq;
        }

        private boolean areAppsEqual(EntitlementGroup group1,
                EntitlementGroup group2) {

            boolean eq = false;

            eq = Util.nullSafeEq(group1.getApplicationName(), group2.getApplicationName(), true);

            if (eq) {
                eq = Util.nullSafeEq(group1.getInstance(), group2.getInstance(), true);
            }

            return eq;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Permission
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Deep equality comparison for Permissions.
     */
    public static class PermissionComparator 
        extends BaseComparator<Permission> {

        public boolean areEqual(Permission p1, Permission p2) {

            boolean eq = false;

            eq = equal(p1.getTarget(), p2.getTarget());

            // in theory these could be csv's with unordered elements
            // just assume simple equality for now
            if (eq)
                eq = equal(p1.getRights(), p2.getRights());
            
            // if annotation changes we need to reflect it
            if (eq)
                eq = equal(p1.getAnnotation(), p2.getAnnotation());

            return eq;
        }

    }

    //////////////////////////////////////////////////////////////////////
    //
    // Scorecard
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Deep equality comparison for Scorecards.
     */
    public static class ScorecardComparator 
        extends BaseComparator<Scorecard> {

        public boolean areEqual(Scorecard sc1, Scorecard sc2) {

            boolean eq = false;

            if (sc1 == null) {
                eq = (sc2 == null);
            }
            else if (sc2 != null) {
                eq = !sc1.isDifferent(sc2);
            }

            return eq;
        }

    }



    //////////////////////////////////////////////////////////////////////
    //
    // PolicyViolation Comparator
    //
    //////////////////////////////////////////////////////////////////////

    public static class PolicyViolationSnapshotComparator extends BaseComparator<PolicyViolation> {
        @Override
        public boolean areEqual(PolicyViolation violation1, PolicyViolation violation2) {
            boolean eq;
            if (violation1 == null) {
                eq = (violation2 == null);
            } else if (violation2 == null) {
                eq = false;
            } else {
                eq = Util.nullSafeEq(violation1.getPolicyId(), violation2.getPolicyId(), true);
                if (eq) {
                    eq = Util.nullSafeEq(violation1.getConstraintId(), violation2.getConstraintId(), true);
                }
            }
            return eq;
        }
    }


    //////////////////////////////////////////////////////////////////////
    //
    // RoleAssignmentSnapshot Comparator
    //
    //////////////////////////////////////////////////////////////////////

    public static class RoleAssignmentSnapshotComparator
            extends BaseComparator<RoleAssignmentSnapshot> {

        @Override
        public boolean areEqual(RoleAssignmentSnapshot snapshot1, RoleAssignmentSnapshot snapshot2) {
            boolean eq;
            if (snapshot1 == null) {
                eq = (snapshot2 == null);
            } else if (snapshot2 == null) {
                eq = false;
            } else {
                eq = Util.nullSafeEq(snapshot1.getName(), snapshot2.getName(), true);
                if (eq) {
                    eq = equal(snapshot1.getTargets(), snapshot2.getTargets(), new RoleTargetComparator());
                }
            }
            return eq;
        }
    }
    //////////////////////////////////////////////////////////////////////
    //
    // RoleTarget Comparator
    //
    //////////////////////////////////////////////////////////////////////
    
    public static class RoleTargetComparator 
            extends BaseComparator<RoleTarget> {
        public boolean areEqual (RoleTarget target1, RoleTarget target2) {
            boolean eq;
            
            if (target1 == null) {
                eq = (target2 == null);
            } else if (target2 == null) {
                eq = false;
            } else {
                eq = Util.nullSafeEq(target1.getApplicationName(), target2.getApplicationName(), true);
                if (eq) {
                    eq = Util.nullSafeEq(target1.getNativeIdentity(), target2.getNativeIdentity(), true);
                }
                if (eq) {
                    eq = Util.nullSafeEq(target1.getInstance(), target2.getInstance(), true);
                }
                if (eq) {
                    eq = equal(target1.getItems(), target2.getItems(), new AccountItemComparator());
                }
            }
            return eq;
        }
    }
    
    public static class AccountItemComparator
    extends BaseComparator<AccountItem> {
        public boolean areEqual(AccountItem item1, AccountItem item2) {
            boolean eq;

            if (item1 == null) {
                eq = (item2 == null);
            } else if (item2 == null) {
                eq = false;
            } else {
                eq = Util.nullSafeEq(item1.isPermission(), item2.isPermission(), true);
                if (eq) {
                    eq = Util.nullSafeEq(item1.getName(), item2.getName(), true);
                }
                if (eq) {
                    eq = Util.nullSafeEq(item1.getValue(), item2.getValue(), true);
                }
            }
            return eq;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // List Filters
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A ListFilter that only includes bundles that reference the application
     * passed into the constructor.
     */
    public static class BundleListFilter implements ListFilter<String> {
        private Application app;
        private SailPointContext ctx;

        /**
         * Constructor.
         *
         * @param app  The Application to use to filter the bundles. Only
         *             bundles that reference this application are included.
         * @param ctx  The SailPointContext to use.
         */
        public BundleListFilter(Application app, SailPointContext ctx) {
            this.app = app;
            this.ctx = ctx;
        }

        /**
         * Return true if the Bundle with the given name does not reference
         * the Application passed into the constructor.
         */
        public boolean removeFromList(String bundleName) throws GeneralException {
            Bundle bundle = ctx.getObjectByName(Bundle.class, bundleName);
            return (null != bundle) && !bundle.referencesApplication(app);
        }
    }

    /**
     * A ListFilter that only includes LinkSnapshots for the application passed
     * into the constructor.
     */
    public static class LinkSnapshotListFilter implements ListFilter<LinkSnapshot> {
        private String appName;

        /**
         * Constructor.
         *
         * @param appName  The name of the Application to use to filter the links.
         *                 Only links that reference this application are included.
         */
        public LinkSnapshotListFilter(String appName) {
            this.appName = appName;
        }

        /**
         * Return true if the given LinkSnapshot does not reference the Application
         * passed into the constructor.
         */
        public boolean removeFromList(LinkSnapshot ls) {
            return (null != this.appName) && !this.appName.equals(ls.getApplication());
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Certification Identity Comparison
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Genarate a list of differences for the simple attributes
     * of two snapshots. Both snapshots must be supplied. If the
     * given application is non-null, only diffs related to the
     * application are returned. This includes top-level attribute
     * diffs, bundles that reference the application, and link
     * differences for the application.
     *
     * The top-level attributes (including organization and manager)
     * are just compiled into a List<Difference>.
     *
     * Entitlement differences are more complicated because they
     * are resource-specific and are stored on the Links. There
     * are two sets, a List<Difference> for the simple Link attributes
     * and a List<Permission> for the more complicated Permission model.
     *
     * @ignore
     * Note that we don't need the EntitlementSnapshots in either
     * the BundleSnapshot or the list of exceptions.  The same information
     * is stored on the LinkSnapshots.  It doesn't seem useful to 
     * display differences in the exceptions or the bundle entitlements, 
     * though I suppose we could structure them that way.  This 
     * would require a more complicated model.  PermissionDifference
     * would need an annotation that said whether it was associated
     * with a Bundle (and if null an exception).  Difference would
     * need two context strings one for Application and one for Bundle.
     */ 
    public IdentityDifference diff(IdentitySnapshot prev, 
                                   IdentitySnapshot current,
                                   Application app,
                                   boolean includePolicyViolations) 
        throws GeneralException {

        IdentityDifference diffs = new IdentityDifference();

        // don't need to diff the _applications property, it
        // is derived from the links

        // start with the extended attribute diffs
        List<Difference> attDiffs =
            Difference.diffMaps(prev.getAttributes(), current.getAttributes(), 
                                null, _maxStringLength);
        diffs.setAttributeDifferences(attDiffs);

        // Diff Bundles and Assigned Roles. Same filter can be used for both since the names
        // in the snapshots will refer to roles
        ListFilter<String> filter =
                (null != app) ? new BundleListFilter(app, _context) : null;
        
        Difference d = getDifference(prev.getBundles(), current.getBundles(), filter);
        if (d != null) {
            diffs.addBundleDifference(d);
        }
        
        d = getDifference(prev.getAssignedRoles(), current.getAssignedRoles(), filter);
        if (d != null) {
            diffs.addAssignedRoleDifference(d);
        }

        // Diff policy violations
        if (includePolicyViolations) {
            List<PolicyViolation> prevViolations = prev.getViolations();
            List<PolicyViolation> newViolations = current.getViolations();
    
            List<String> prevViolationNames = getViolationNames(prevViolations);
            List<String> newViolationNames = getViolationNames(newViolations);
    
            d = Difference.diff(prevViolationNames, newViolationNames, _maxStringLength);
            if (d != null) {
                diffs.addPolicyViolationDifference(d);
            }
        }

        // set display names for Identity attributes
        setDisplayNames(diffs.getAttributeDifferences());

        // diff the links
        ListFilter<LinkSnapshot> linkFilter =
            (null != app) ? new LinkSnapshotListFilter(app.getName()) : null;
        List<LinkSnapshot> prevLinks = Util.filter(prev.getLinks(), linkFilter);
        List<LinkSnapshot> newLinks = Util.filter(current.getLinks(), linkFilter);
        if (prevLinks == null) {
            // everything's new baby!   
            if (newLinks != null) {
                for (LinkSnapshot ls : newLinks)
                    diffLinks(diffs, null, ls);
            }
        }
        else if (newLinks == null) {
            // it's just gone man, all gone
            if (prevLinks != null) {
                for (LinkSnapshot ps : prevLinks)
                    diffLinks(diffs, ps, null);
            }
        }
        else {
            // make a copy so we can modify it
            prevLinks = new ArrayList<LinkSnapshot>(prevLinks);
            for (LinkSnapshot ls : newLinks) {
                LinkSnapshot ps = getLinkSnapshot(prevLinks, ls);
                diffLinks(diffs, ps, ls);
                if (ps != null)
                    prevLinks.remove(ps);
            }
            // anything left over has been removed
            for (LinkSnapshot ps : prevLinks)
                diffLinks(diffs, ps, null);
        }


        return diffs;
    }

    /**
     * Get the Difference object from the names of a list of NamedSnapshots
     */
    private Difference getDifference(List<? extends NamedSnapshot> oldSnapshots, List<? extends NamedSnapshot> newSnapshots, ListFilter<String> filter)
            throws GeneralException {

        List<String> prevNames = Util.filter(getNames(oldSnapshots), filter);
        List<String> newNames = Util.filter(getNames(newSnapshots), filter);

        return Difference.diff(prevNames, newNames, _maxStringLength);
    }

    /**
     * Convert a list of NamedSnapshot objects into a list of role name
     */
    private List<String> getNames(List<? extends NamedSnapshot> snapshots) {

        List<String> names = null;
        if (snapshots != null) {
            names = new ArrayList<String>();
            for (NamedSnapshot snapshot : snapshots)
                names.add(snapshot.getName());
        }
        return names;
    }

    /**
     * Convert a list of PolicyViolation objects into a list
     * of policy violation names.
     */
    private List<String> getViolationNames(List<PolicyViolation> violations)
        throws GeneralException {
        
        List<String> names = null;
        if (violations != null) {
            names = new ArrayList<String>();
            for (PolicyViolation pv : violations)
                names.add(pv.getDisplayableName());
        }
        return names;
    }

    /**
     * Decorate a list of diffs for the top-level Identity
     * attribute with display names from the ObjectConfig.
     * This is also where you might want to filter out residual
     * attribute that are no longer in the config?
     */
    private void setDisplayNames(List<Difference> diffs) 
        throws GeneralException {

        if (_context != null && diffs != null) {
            // locate the ObjectConfig so we can include display names
            ObjectConfig idconfig =
                _context.getObjectByName(ObjectConfig.class,
                                   ObjectConfig.IDENTITY);

            if (idconfig != null) {
                for (Difference d : diffs) {
                    ObjectAttribute ia = idconfig.getObjectAttribute(d.getAttribute());
                    if (ia != null) 
                        d.setDisplayName(ia.getDisplayName());
                }
            }
        }
    }

    /**
     * Locate the LinkSnapshot for an Application from a list.
     * Only the application has to match, not supporting multiple accounts
     * per application (yet anyway...)
     */
    private LinkSnapshot getLinkSnapshot(List<LinkSnapshot> links,
                                         LinkSnapshot src) {
        
        LinkSnapshot found = null;
        if (links != null && src != null) {
            String srcapp = src.getApplication();
            if (srcapp != null) {
                for (LinkSnapshot ls : links) {
                    if (srcapp.equals(ls.getApplication())) {
                        found = ls;
                        break;
                    }
                }
            }
        }
        return found;
    }

    /**
     * Return a list of attributes found on a LinkSnapshot 
     * that should not be included in the difference calculations.
     * Cached since it never changes.    
     */
    private List getLinkExclusions() {

        if (_linkExclusions == null) {
            _linkExclusions = new ArrayList();
            _linkExclusions.add(Connector.ATTR_DIRECT_PERMISSIONS);
            _linkExclusions.add(Connector.ATTR_TARGET_PERMISSIONS);
        }
        return _linkExclusions;
    }

    /**
     * Helper for diff(IdentitySnapshot), calculate the differences
     * between two LinkSnapshots. The snapshots must be for the
     * same Application, and either can be null.
     */
    private void diffLinks(IdentityDifference diffs,
                           LinkSnapshot old, LinkSnapshot neu) {

        // Determine the name of the application for annotating
        // the difference objects.
        String appname = null;
        String nativeId = null;
        if (neu != null) {
            appname = neu.getApplication();
            nativeId = neu.getNativeIdentity();
        }
        else if (old != null) {
            appname = old.getApplication();
            nativeId = old.getNativeIdentity();
        }

        // handle simple attributes with a map comparison, 
        // excluding the permission attributes and other things
        // that are typically found in a Link but aren't relevant here
        Attributes oldAttrs = (old != null) ? old.getAttributes() : null;
        Attributes newAttrs = (neu != null) ? neu.getAttributes() : null;
        List<Difference> attDiffs = 
            Difference.diffMaps(oldAttrs, newAttrs, getLinkExclusions(), _maxStringLength);

        // decorate each with the application name as the "context" 
        // for display
        if (attDiffs != null) {
            String context = IdentityDifference.generateContext(appname, 
                    (_includeNativeIdentity) ? nativeId : null);

            for (Difference d : attDiffs)
                d.setContext(context);
        }

        diffs.addLinkDifferences(attDiffs);

        // now dig out the permissions
        List<Permission> oldPerms = (old != null) ? getPermissions(old) : null;
        List<Permission> newPerms = (neu != null) ? getPermissions(neu) : null;

        // generate permission diffs for each matching permission, 
        // hey, where have I seen this pattern before...
        if (oldPerms == null) {
            if (newPerms != null) {
                for (Permission np : newPerms)
                    diffPermission(diffs, appname, null, np);
            }
        }
        else if (newPerms == null) {
            if (oldPerms != null) {
                for (Permission op : oldPerms)
                    diffPermission(diffs, appname, op, null);
            }
        }
        else {
            // matching permissions assumes that there can be only
            // one Permission object per target
            oldPerms = new ArrayList<Permission>(oldPerms);
            for (Permission np : newPerms) {
                Permission op = getPermission(oldPerms, np);
                diffPermission(diffs, appname, op, np);
                if (op != null)
                    oldPerms.remove(op);
            }
            for (Permission op : oldPerms)  
                diffPermission(diffs, appname, op, null);
        }
    }

    /**
     * Helper for diffLinks, extract a list of Permission objects
     * held by an account.  
     *
     * In theory there are several lists to merge, depending on
     * how the resource implements permissions.
     */
    private List<Permission> getPermissions(LinkSnapshot link) {

        List<Permission> allPerms = new ArrayList<Permission>();

        Attributes atts = link.getAttributes();
        if (atts != null) {
            List<Permission> perms;

            // TODO: probably should be filtering here?
            perms = (List<Permission>)atts.get(Connector.ATTR_DIRECT_PERMISSIONS);
            if (perms != null)
                allPerms.addAll(perms);
                            
            perms = (List<Permission>)atts.get(Connector.ATTR_TARGET_PERMISSIONS);
            if (perms != null)
                allPerms.addAll(perms); 
        }

        return allPerms;
    }

    /**
     * Helper for diffLinks, search a list for a Permission
     * with a matching target.
     */
    private Permission getPermission(List<Permission> perms, 
                                     Permission src) {
        Permission found = null;
        if (perms != null && src != null) {
            String target = src.getTarget();
            if (target != null) {
                for (Permission p : perms) {
                    if (target.equals(p.getTarget())) {
                        found = p;
                        break;
                    }
                }
            }
        }
        return found;
    }

    /**
     * Helper for diffLinks, compare two Permissions. Either Permission
     * can be null. We will generate multiple PermissionDifference objects
     * for each right difference.
     */
    private void diffPermission(IdentityDifference diffs,
                                String application,
                                Permission p1, Permission p2) {

        PermissionDifference pd;
        String target = null;
        List<String> oldRights = null;
        List<String> newRights = null;

        if (p1 != null) {
            target = p1.getTarget();
            oldRights = p1.getRightsList();
            // we need to modify this as we diff
            if (oldRights != null)
                oldRights = new ArrayList<String>(oldRights);
        }
        if (p2 != null) {
            target = p2.getTarget();
            newRights = p2.getRightsList();
        }

        if (oldRights == null) {
            if (newRights != null) {
                // all added
                for (String right : newRights) {
                    pd = new PermissionDifference();
                    pd.setApplication(application);
                    pd.setTarget(target);
                    pd.setRights(right);
                    diffs.add(pd);
                }
            }
        }
        else if (newRights == null) {
            // all removed
            for (String right : oldRights) {
                pd = new PermissionDifference();
                pd.setApplication(application);
                pd.setTarget(target);
                pd.setRights(right);
                pd.setRemoved(true);
                diffs.add(pd);
            }
        }
        else {
            for (String right : newRights) {
                if (oldRights.contains(right))
                    oldRights.remove(right);
                else {
                    pd = new PermissionDifference();
                    pd.setApplication(application);
                    pd.setTarget(target);
                    pd.setRights(right);
                    diffs.add(pd);
                }
            }
            // leftovers have been removed
            for (String right : oldRights) {
                pd = new PermissionDifference();
                pd.setApplication(application);
                pd.setTarget(target);
                pd.setRights(right);
                pd.setRemoved(true);
                diffs.add(pd);
            }
        }

    }

}
