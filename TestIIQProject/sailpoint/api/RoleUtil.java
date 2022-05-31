/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Various utilities related to roles and their relationships.
 *
 * Author: Jeff
 *
 * If you have something related to role editing it probably belongs
 * in RoleLifecycler.  This file should contain things for analyzing
 * the role model in support of the request UI or workflow customizations.
 *
 */
package sailpoint.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.search.BeanutilsMatcher;
import sailpoint.search.Matcher;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Various utilities related to roles and their relationships.
 */
public class RoleUtil {

    private static final Log log = LogFactory.getLog(RoleUtil.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Requestable Roles
    //
    //////////////////////////////////////////////////////////////////////
    
    /** 
     * Calculate a list of role objects that are permitted by the
     * currently assigned roles and not already assigned or requested.
     *
     * QueryOptions is optional and can be used to pass Filters
     * to restrict the set of roles returned.  This is what LCM uses
     * to combine the result of an assignable role query with
     * the requestable permits, applying the filters consistently
     * to both results.
     */
    public static List<Bundle> getRequestablePermits(Identity ident,
                                                     QueryOptions ops)
        throws GeneralException {

        List<Bundle> requestable = new ArrayList<Bundle>();
        List<Bundle> assigned = ident.getAssignedRoles();

        // assignment list shouldn't be long but we can't depend
        // on that so avoid linear searches
        Map<Bundle,Bundle> assmap = new HashMap<Bundle,Bundle>();
        if (assigned != null) {
            for (Bundle b : assigned)
                assmap.put(b, b);

            // TODO: Need to get with Dan and see if we have a model
            // for requsted permitted roles that is different
            // from the assignment list.  Until then assume that
            // anything we find on the detected role list should
            // also be filtered.
            List<Bundle> detected = ident.getDetectedRoles();
            if (detected != null) {
                for (Bundle b : detected)
                    assmap.put(b, b);
            }
            
            // convert the QueryOptions into a Matcher
            Matcher matcher = null;
            if (ops != null) {
                List<Filter> filters = ops.getFilters();
                if (filters != null) {
                    if (filters.size() == 1)
                        matcher = new BeanutilsMatcher(filters.get(0));
                    else if (filters.size() > 1) {
                        Filter all = Filter.and(filters);
                        matcher = new BeanutilsMatcher(all);
                    }
                }
            }
                        
            // now recursive walk over the assignments
            for (Bundle b : assigned)
                getRequestables(b, matcher, assmap, requestable);
        }

        return requestable;
    }

    /**
     * Checks whether or not the given role is detectable.  A role is considered
     * detectable if the Bundle ObjectConfig contains a RoleTypeDefinition corresponding
     * to the given role's type that has its "detectable" value set to true
     * @param role Bundle being checked
     * @return true if the role is known to be detectable; false otherwise
     */
    public static boolean isDetectable(Bundle role) {
        ObjectConfig roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
        RoleTypeDefinition roleType = roleConfig.getRoleType(role);
        boolean isDetectable;
        if (roleType != null) {
            isDetectable = roleType.isDetectable();
        } else {
            // Be safe and assume that untyped roles are always undetectable
            isDetectable = false;
        }
        return isDetectable;
    }

    /**
     * Checks whether or not the given role is detectable.  A role is considered
     * detectable if the Bundle ObjectConfig contains a RoleTypeDefinition corresponding
     * to the given role's type that has its "noDetectionUnlessAssigned" value set to true
     * @param role Bundle being checked
     * @return true if the role is only detectable when assigned; false otherwise
     */
    public static boolean isDetectableOnlyWhenAssigned(Bundle role) {
        ObjectConfig roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
        RoleTypeDefinition roleType = roleConfig.getRoleType(role);
        boolean isDetectable;
        if (roleType != null) {
            isDetectable = roleType.isNoDetectionUnlessAssigned();
        } else {
            // Be safe and assume that untyped roles are always undetectable
            isDetectable = false;
        }
        return isDetectable;
    }

    /**
     * Given one assigned role, add the roles it permits to the
     * requestables list.  This will walk up the inheritance hierarcny 
     * to find inherited permits.  The model supports permitted roles 
     * having their own permits list but this is not recommended
     * practice and I'm not sure what that would even mean.  So we
     * only go through one level of permits.  Similarly required
     * roles could have permits but we don't support that either.
     *
     * The role is expected to be a business role with one level
     * of permits.
     */
    private static void getRequestables(Bundle role,
                                        Matcher matcher,
                                        Map<Bundle,Bundle> assigned,
                                        List<Bundle> requestable)
        throws GeneralException {

        List<Bundle> permits = role.getPermits();
        if (permits != null) {
            for (Bundle p : permits) {
                // not if already assigned or encountered
                if (assigned.get(p) == null) {
                    assigned.put(p, p);
                    // only if it passes the filter
                    if (matcher == null || matcher.matches(p))
                        requestable.add(p);
                }
            }
        }

        List<Bundle> inheritance = role.getInheritance();
        if (inheritance != null) {
            for (Bundle i : inheritance)
                getRequestables(i, matcher, assigned, requestable);
        }
    }
    
    /**
     * Return a flattened permitted role list of this and all inherited roles
     */
    public static List<Bundle> getFlattenedPermittedRoles(SailPointContext context, Bundle role, List<Bundle> permits) throws GeneralException {
        
        if(null == permits) {
            permits = new ArrayList<Bundle>();
        }
        
        if(role != null && permits != null) {
            permits.addAll(role.getPermits());
        } else {
            return permits;
        }
        
        List<Bundle> superRoles = role.getInheritance();
        //Walk the super roles to this role to ensure we include all permitted roles
        for(Bundle superRole : Util.iterate(superRoles)) {
            getFlattenedPermittedRoles(context, superRole, permits);
        }
        
        Util.removeDuplicates(permits);
        
        return permits;
    }

}
