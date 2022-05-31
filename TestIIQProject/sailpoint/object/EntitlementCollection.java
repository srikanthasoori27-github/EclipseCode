/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An EntitlementCollection is a non-persistent class that holds entitlements
 * grouped by application, instance, and nativeIdentity. This provides functionality such
 * as merging entitlements when adding new entitlements, and removing pieces of
 * entitlements. 
 */
public class EntitlementCollection implements Serializable
{
    /**
     * Mapping from application -> nativeIdentity -> EntitlementSnapshot.
     * jsl - with the introduction of template applications the key here
     * might actually be a composite key for the form "application/instance"
     */
    private Map<String,Map<String,EntitlementSnapshot>> entitlements;

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor.
     */
    public EntitlementCollection()
    {
        this.entitlements = new HashMap<String,Map<String,EntitlementSnapshot>>(); 
    }

    /**
     * Constructor that initializes with a collections of entitlement snapshots.
     * 
     * @param  snaps  The EntitlementSnapshots to use to initialize this object.
     */
    public EntitlementCollection(Collection<EntitlementSnapshot> snaps)
    {
        this();
        if (null != snaps)
        {
            for (EntitlementSnapshot es : snaps)
                addEntitlements(es);
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // PUBLIC METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Clear all entitlements.
     */
    public void clear()
    {
        this.entitlements.clear();
    }

    /**
     * Return whether this EntitlementCollection is empty.
     */
    public boolean isEmpty() {
        // Empty entitlement groups are pruned, so we can just check
        // the entitlements collection.
        return this.entitlements.isEmpty();
    }
    
    /**
     * Get all entitlements.
     */
    public Collection<EntitlementSnapshot> getEntitlements()
    {
        List<EntitlementSnapshot> ents = new ArrayList<EntitlementSnapshot>();

        for (Map<String,EntitlementSnapshot> map : this.entitlements.values()) {
            for (EntitlementSnapshot es : map.values()) {
                ents.add(es);
            }
        }

        return ents;
    }

    /**
     * Get all entitlements as EntitlementGroups instead of EntitlementSnapshots.
     * 
     * @param  res  The Resolver used to resolve objects.
     */
    public List<EntitlementGroup> getEntitlementGroups(Resolver res)
        throws GeneralException
    {
        List<EntitlementGroup> groups = new ArrayList<EntitlementGroup>();

        for (EntitlementSnapshot es : getEntitlements())
        {
            if (((null != es.getPermissions()) && !es.getPermissions().isEmpty()) ||
                ((null != es.getAttributes()) && !es.getAttributes().isEmpty()))
            {
                groups.add(new EntitlementGroup(res, es));
            }
        }

        return groups;
    }

    /**
     * Add the contents of another collection into this one.
     */
    public void add(EntitlementCollection other) {
        
        if (other != null) {
            Collection<EntitlementSnapshot> snaps = other.getEntitlements();
            if (snaps != null) {
                for (EntitlementSnapshot snap : snaps)
                    addEntitlements(snap);
            }
        }
    }

    /**
     * Add the given entitlements to this collection.
     */
    public void addEntitlements(EntitlementSnapshot entitlement)
    {
        String application = entitlement.getApplication();
        String instance = entitlement.getInstance();
        String nativeIdentity = entitlement.getNativeIdentity();
        String displayName = entitlement.getDisplayName();

        addAttributes(application, instance, nativeIdentity, displayName, entitlement.getAttributes());
        addPermissions(application, instance, nativeIdentity, displayName, entitlement.getPermissions());
    }

    /**
     * Merge an attribute value into this collection.
     */
    public void addAttribute(String app, String instance, String nativeIdentity, 
                             String displayName, String attrName, Object value) {

        Attributes<String,Object> attrs = new Attributes<String,Object>();
        attrs.put(attrName, value);
        addAttributes(app, instance, nativeIdentity, displayName, attrs);
    }

    /**
     * Merge the attribute values from the given Map into this collection.
     */
    public void addAttributes(String app, String instance, String nativeIdentity,
                              String displayName, Map<String,Object> newAttrs)
    {
        if (null == newAttrs)
            return;

        Map<String,Object> thisAttrs = getAttributes(app, instance, nativeIdentity, displayName);

        // Clone if we don't have any yet, otherwise merge them
        if (thisAttrs.isEmpty()) {
            thisAttrs.putAll(new Attributes<String,Object>(newAttrs).mediumClone());
        }
        else {
            for (Map.Entry<String, Object> entry : newAttrs.entrySet())
            {
                String attrName = entry.getKey();
                Object newVal = entry.getValue();
                thisAttrs = mergeValues(attrName, newVal, thisAttrs);
            }
        }

        // Make sure we're not storing empty entitlements after we modify the
        // entitlements collections.
        pruneEmptyEntitlements(app, instance, nativeIdentity, displayName);
    }

    /**
     * Add the given key/value pair to the given map, merging the new value into
     * a collection if there is already a value for the given attribute.
     * 
     * @param  attrName  The name of the attribute to merge.
     * @param  newVal    The value of the attribute being merged.
     * @param  mergeMap  The map into which to merge the attribute.
     * 
     * @return The merged map.
     */
    @SuppressWarnings("unchecked")
    public static Map<String,Object> mergeValues(String attrName, Object newVal,
                                                 Map<String,Object> mergeMap) {

        Object existingVal = mergeMap.get(attrName);

        if (null != existingVal)
        {
            if (existingVal instanceof Collection)
            {
                if (newVal instanceof Collection) {
                    // IIQHH-1170: Make sure newVal & existingVal aren't the same object.
                    // There's no need to add all elements from a collection into itself.
                    if (newVal != existingVal) {
                        ((Collection) existingVal).addAll((Collection) newVal);
                    }
                }
                else {
                    ((Collection) existingVal).add(newVal);
                }
            }
            else if(newVal != null)
            {
                if (!newVal.equals(existingVal))
                {
                    Set set = new HashSet();
                    set.add(existingVal);
                    if (newVal instanceof Collection)
                        set.addAll((Collection) newVal);
                    else
                        set.add(newVal);
                    mergeMap.put(attrName, set);
                }
            } else {
                mergeMap.put(attrName, newVal);
            }
        }
        else
        {
            mergeMap.put(attrName, newVal);
        }

        return mergeMap;
    }

    /**
     * Merge the given Permission into this collection.
     */
    public void addPermission(String app, String instance, String nativeIdentity, String displayName,
                              Permission p) {
        addPermissions(app, instance, nativeIdentity, displayName, Collections.singletonList(p));
    }

    /**
     * Merge the given Permissions into this collection.
     */
    public void addPermissions(String app, String instance, String nativeIdentity,
                               String displayName, List<Permission> perms) {
        
        if (null == perms)
            return;

        List<Permission> thisPerms = getPermissions(app, instance, nativeIdentity, displayName);

        // Clone if we don't have any yet, otherwise merge them
        if (thisPerms.isEmpty()) {
            thisPerms.addAll(Permission.clone(perms));
        }
        else {
            for (Permission p : perms) {
                // Roll up permission rights per target.
                boolean foundTargetMatch = false;
                for (Permission thisPerm : thisPerms)
                {
                    if (p.getTarget().equals(thisPerm.getTarget()))
                    {
                        Set<String> thisRights =
                            new HashSet<String>(Util.csvToList(thisPerm.getRights()));
                        thisRights.addAll(Util.csvToList(p.getRights()));
                        thisPerm.setRights(Util.listToCsv(new ArrayList<String>(thisRights)));
                        foundTargetMatch = true;
                    }
                }
        
                // If we didn't roll up - just add the permission.
                if (!foundTargetMatch)
                    thisPerms.add(p);
            }
        }

        // Make sure we're not storing empty entitlements after we modify the
        // entitlements collections.
        pruneEmptyEntitlements(app, instance, nativeIdentity, displayName);
    }

    /**
     * Remove all entitlements from the given EntitlementCollection from this
     * EntitlementCollection.
     */
    public void remove(EntitlementCollection ec)
    {
        if (ec != null && ec.entitlements != null) {
            // Remove all permissions that are in the given collection.
            for (Map.Entry<String,Map<String,EntitlementSnapshot>> entry : Util.iterate(ec.entitlements.entrySet()))
            {
                // Sigh, have to split the application/instance composite
                // key but we'll just merge it again when we call getPermissions(), etc.
                // Actually we could cheat and just leave application with the composite
                // key, it will still be used properly for lookup, but it feels funy...
                String key = entry.getKey();
                String application = getKeyApplication(key);
                String instance = getKeyInstance(key);
    
                for (Map.Entry<String,EntitlementSnapshot> e2 : entry.getValue().entrySet()) {
                    String nativeIdentity = e2.getKey();
                    EntitlementSnapshot es = e2.getValue();
        
                    List<Permission> permsToRemove = es.getPermissions();
                    List<Permission> thisPerms = getPermissions(application, instance, nativeIdentity, es.getDisplayName());
        
                    if ((null != permsToRemove) && (null != thisPerms))
                    {
                        List<Permission> toAddBack = new ArrayList<Permission>();
        
                        for (Permission permToRemove : permsToRemove)
                        {
                            for (Iterator<Permission> permIt=thisPerms.iterator(); permIt.hasNext(); )
                            {
                                Permission thisPerm = permIt.next();
                                if ((null != thisPerm.getTarget()) && thisPerm.getTarget().equals(permToRemove.getTarget()))
                                {
                                    permIt.remove();
        
                                    // If this is not an exact match, we'll need to prune
                                    // off the matched values and re-store it in the map.
                                    if ((null != thisPerm.getRights()) &&
                                        !thisPerm.getRights().equals(permToRemove.getRights()))
                                    {
                                        List<String> thisRights = Util.csvToList(thisPerm.getRights());
                                        List<String> rightsToRemove = Util.csvToList(permToRemove.getRights());
                                        if (null != rightsToRemove)
                                            thisRights.removeAll(rightsToRemove);
        
                                        // Only add this back if there are any rights left.
                                        if (!thisRights.isEmpty())
                                        {
                                            thisPerm.setRights(Util.listToCsv(thisRights));
                                            toAddBack.add(thisPerm);
                                        }
                                    }
                                }
                            }
                        }
                        thisPerms.addAll(toAddBack);
                    }
        
                    // Remove attributes values found in the given entitlement collection.
                    Attributes<String,Object> attrsToRemove = ec.getAttributes(application, instance, nativeIdentity,
                            es.getDisplayName());
                    Attributes<String,Object> thisAttrs = getAttributes(application, instance, nativeIdentity,
                            es.getDisplayName());
        
                    if ((null != attrsToRemove) && (null != thisAttrs))
                    {
                        Attributes<String,Object> attrsToAddBack = new Attributes<String,Object>();
        
                        for (Map.Entry<String,Object> toRemoveEntry : attrsToRemove.entrySet())
                        {
                            String attr = toRemoveEntry.getKey();
                            Object toRemoveValue = toRemoveEntry.getValue();
        
                            Object thisVal = thisAttrs.remove(attr);
                            if (thisVal instanceof Collection)
                            {
                                //Make sure we use *something* that supports remove 
                                //so we can make sure and not cause
                                //an UnsupportedOperationException
                                List<Object> thisValList = new ArrayList<Object>((Collection)thisVal);
                                if(toRemoveValue instanceof Collection) {
                                    thisValList.removeAll((Collection)toRemoveValue);
                                } else {
                                    thisValList.remove(toRemoveValue);
                                }
                                
                                if(!thisValList.isEmpty()) {
                                    if (thisValList.size() > 1) {
                                        attrsToAddBack.put(attr, thisValList);
                                    }
                                    else {
                                        // If we only have one value, down-grade from a collection.
                                        attrsToAddBack.put(attr, thisValList.iterator().next());
                                    }
                                }
    
                            }
                            else if (thisVal != null) {
                                // scalar value
                                // handling thisVal == null here though I don't think
                                // we need to - jsl
                                if (toRemoveValue instanceof Collection) {
                                    if (!((Collection)toRemoveValue).contains(thisVal))
                                        attrsToAddBack.put(attr, thisVal);
                                }
                                else if (toRemoveValue != null) {
                                    if (!toRemoveValue.equals(thisVal))
                                        attrsToAddBack.put(attr, thisVal);
                                }
                                else {
                                    // toRemoveValue was null, thisVal is non-null,
                                    // so put it back
                                    attrsToAddBack.put(attr, thisVal);
                                }
                            }
                        }
                        thisAttrs.putAll(attrsToAddBack);
                    }
        
                    // If there is nothing left in the entitlement snapshot, remove it.
                    pruneEmptyEntitlements(application, instance, nativeIdentity, null);
                }
            }
        }
    }

    /**
     * Return whether this EntitlementCollection contains any of the
     * entitlements in the given EntitlementCollection.
     * 
     * @param  ec  The EntitlementCollection with the entitlements to check.
     * 
     * @return True if this EntitlementCollection contains at least one of the
     *         entitlements in the given EntitlementCollection, false otherwise.
     */
    public boolean containsAny(EntitlementCollection ec) {
        
        boolean containsAny = false;

        if (null != ec) {

            // Iterate over all of the snapshots in the given collection.
            // For each snapshot:
            //  - Look for a snapshot in this collection for the same account.
            //  - Check if the found snapshot has any of the same attributes or
            //    permissions.
            Collection<EntitlementSnapshot> ents = ec.getEntitlements();
            if (null != ents) {
                for (EntitlementSnapshot snap : ents) {

                    EntitlementSnapshot thisSnap =
                        this.getEntitlements(snap.getApplication(), snap.getInstance(), snap.getNativeIdentity(),
                                snap.getDisplayName());
                    containsAny = containsAny(thisSnap, snap);
                    if (containsAny) {
                        break;
                    }
                }
            }
        }
        
        return containsAny;
    }

    @Override
    public String toString()
    {
        return this.entitlements.toString();
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // PRIVATE METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Check if e1 contains any of the same attribute values or permission
     * rights as e2.
     */
    private boolean containsAny(EntitlementSnapshot e1, EntitlementSnapshot e2) {
        
        boolean containsAny = false;

        if ((null != e1) && (null != e2)) {
            Attributes<String,Object> a1 = e1.getAttributes();
            Attributes<String,Object> a2 = e2.getAttributes();

            if ((null != a1) && (null != a2)) {
                for (String attrName : a1.getKeys()) {
                    Object val1 = a1.get(attrName);
                    Object val2 = a2.get(attrName);

                    if ((null != val1) && (null != val2)) {
                        containsAny = containsAnyValues(val1, val2);
                        if (containsAny) {
                            break;
                        }
                    }
                }
            }

            List<Permission> p1 = e1.getPermissions();
            List<Permission> p2 = e2.getPermissions();
            if ((null != p1) && (null != p2)) {
                containsAny = containsAnyPermissions(p1, p2);
            }
        }

        return containsAny;
    }

    /**
     * Return whether o1 contains any of the same values as o2 (if it is a
     * collection) or if the values are equal (if it is not a collection).
     */
    private boolean containsAnyValues(Object o1, Object o2) {

        boolean containsAny = false;

        if ((null != o1) && (null != o2)) {
            if (o1 instanceof Collection) {
                Collection c1 = (Collection) o1;

                if (o2 instanceof Collection) {
                    containsAny = Util.containsAny(c1, (Collection) o2);
                }
                else {
                    containsAny = c1.contains(o2);
                }
            }
            else {
                if (o2 instanceof Collection) {
                    Collection<Object> c1 = new ArrayList<Object>();
                    c1.add(o1);
                    containsAny = Util.containsAny(c1, (Collection) o2);
                }
                else {
                    containsAny = o1.equals(o2);
                }
            }
        }

        return containsAny;
    }

    /**
     * Return whether the first permission list contains any permission rights
     * found in the second permission list.
     */
    private boolean containsAnyPermissions(List<Permission> perms1,
                                           List<Permission> perms2) {
        
        boolean containsAny = false;
        
        if ((null != perms1) && (null != perms2)) {
            for (Permission p1 : perms1) {
                Permission p2 = getPermissionWithTarget(perms2, p1.getTarget());
                if (null != p2) {
                    containsAny =
                        Util.containsAny(p1.getRightsList(), p2.getRightsList());
                    if (containsAny) {
                        break;
                    }
                }
            }
        }

        return containsAny;
    }

    /**
     * Get the permission from the list with the given target, or null.
     */
    private Permission getPermissionWithTarget(List<Permission> perms,
                                               String target) {
        
        Permission p = null;

        if ((null != perms) && (null != target)) {
            for (Permission current : perms) {
                if (target.equals(current.getTarget())) {
                    p = current;
                    break;
                }
            }
        }

        return p;
    }

    /**
     * Get the EntitlementSnapshot for an application.
     * 
     * @param  application     The name of the application for which to retrieve
     *                         the EntitlementSnapshot.
     * @param  instance        Optional instance for template applications
     * @param  nativeIdentity  The nativeIdentity to retrieve.
     * @param  displayName     Optional display name for the account
     */
    private EntitlementSnapshot getEntitlements(String application,
                                                String instance,
                                                String nativeIdentity,
                                                String displayName)
    {
        // Now that we have template app instance ids, have to 
        // combine keys.  Could do it in either map I guess.
        String key = getKey(application, instance);
        Map<String,EntitlementSnapshot> map = this.entitlements.get(key);
        if (null == map) {
            map = new HashMap<String,EntitlementSnapshot>();
            this.entitlements.put(key, map);
        }

        EntitlementSnapshot entitlement = map.get(nativeIdentity);
        if (null == entitlement)
        {
            entitlement = new EntitlementSnapshot(application, instance, nativeIdentity, displayName);
            map.put(nativeIdentity, entitlement);
        }
        return entitlement;
    }

    /**
     * Get the Attributes for the given Application.
     * 
     * @param  application     The name of the application for which to retrieve
     *                         the attributes.
     * @param  instance        Optional template application instance.
     * @param  nativeIdentity  The nativeIdentity to retrieve.
     * @param  displayName     Optional display name for the account
     */
    private Attributes<String,Object> getAttributes(String application,
                                                    String instance,
                                                    String nativeIdentity,
                                                    String displayName)
    {
        EntitlementSnapshot es = getEntitlements(application, instance, nativeIdentity, displayName);
        Attributes<String,Object> attrs = es.getAttributes();
        if (null == attrs)
        {
            attrs = new Attributes<String,Object>();
            es.setAttributes(attrs);
        }
        return attrs;
    }

    /**
     * Get the Attributes for the given Application.
     * 
     * @param  application     The name of the application for which to retrieve
     *                         the attributes.
     * @param  instance        Optional template application instance.
     * @param  nativeIdentity  The nativeIdentity to retrieve.
     * @param  displayName     Optional display name for the account
     */
    private List<Permission> getPermissions(String application,
                                            String instance,
                                            String nativeIdentity,
                                            String displayName)
    {
        EntitlementSnapshot es = getEntitlements(application, instance, nativeIdentity, displayName);
        List<Permission> perms = es.getPermissions();
        if (null == perms)
        {
            perms = new ArrayList<Permission>();
            es.setPermissions(perms);
        }
        return perms;
    }

    /**
     * Remove the entitlements for the given app from this collection if there
     * are no entitlements they are empty.
     * 
     * @param  app             The application name for which to prune the
     *                         entitlements.
     * @param  instance        Optional application template instance.
     * @param  nativeIdentity  The nativeIdentity to prune.
     * @param  displayName     Optional user-friendly display name for the account
     */
    private void pruneEmptyEntitlements(String app, String instance, String nativeIdentity, String displayName)
    {
        EntitlementSnapshot es = getEntitlements(app, instance, nativeIdentity, displayName);
        if ((null != es.getAttributes()) && es.getAttributes().isEmpty())
            es.setAttributes(null);
        if ((null != es.getPermissions()) && es.getPermissions().isEmpty())
            es.setPermissions(null);

        if (es.isEmpty()) {
            String key = getKey(app, instance);
            Map<String,EntitlementSnapshot> byApp = this.entitlements.get(key);
            if (null != byApp) {
                byApp.remove(nativeIdentity);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Application/Instance key utilities
    //
    ////////////////////////////////////////////////////////////////////////////
    /*
     * jsl - I made these public because we need something similar
     * over in AccountIdBean and possibly elsewhere, but they didn't
     * seem big enough to split into a utility class.
     */

    /**
     * Token used between the application and instance names in
     * composite keys.
     *
     * @ignore
     * !! Need an unambiguous delimiter so we can reliably
     * parse the two halves later.  This will have to become
     * part of the documented restrictions on application names.
     */
    public static final String KEY_DELIMITER = "::"; 

    /**
     * Return the key used to lookup things in the entitlements cache.
     * Normally this is just the application name but if it is
     * dealing with template applications it can also include the
     * instance name.
     */
    public static String getKey(String application, String instance) {

        String key = application;
        if (instance != null)
            key = application + KEY_DELIMITER + instance;
        return key;
    }

    /**
     * Return the application name from a composite key.
     */
    public static String getKeyApplication(String key) {
        String application = key;
        if (key != null) {
            int psn = key.lastIndexOf(KEY_DELIMITER);
            if (psn > 0)
                application = key.substring(0, psn);
        }
        return application;
    }

    public static String getKeyInstance(String key) {
        String instance = null;
        if (key != null) {
            int psn = key.lastIndexOf(KEY_DELIMITER);
            if (psn > 0) {
                int first = psn + KEY_DELIMITER.length();
                if (first < key.length())
                    instance = key.substring(first);
            }
        }
        return instance;
    }


}
