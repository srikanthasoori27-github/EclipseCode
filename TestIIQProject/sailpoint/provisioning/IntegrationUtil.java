/* (c) Copyright 2008-2009 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Utility methods related to roles synchronization that must
 * be shared by Provisioner and sailpoint.task.RoleSynchronizer.
 *
 * Author: Jeff
 *
 */

package sailpoint.provisioning;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

public class IntegrationUtil {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    // 
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(IntegrationUtil.class);

    /**
     * Returns true if this role matches the filter and inheritance
     * constraint.  
     *
     */
    static public boolean isFilteredRole(SailPointContext context,
                                         IntegrationConfig integration, 
                                         Bundle role)
        throws GeneralException {

        boolean filtered = false;

        // should this trump the other selectors, or do we 
        // merge them?
        List<Bundle> roles = integration.getSynchronizedRoles();
        if (roles != null && roles.size() > 0) {
            filtered = roles.contains(role);
        }
        else {
            Filter filter = integration.getRoleSyncFilter();
            Bundle container = integration.getRoleSyncContainer();

            if (filter == null && container == null) {
                // all roles
                filtered = true;
            }
            else {
                // Since evaluating the Filter in memory is unreliable
                // have to make Hibernate do it by adding an id
                // comparison and looking for one result row.
                boolean passesFilter = true;
                if (filter != null) {
                    QueryOptions ops = new QueryOptions();
                    Filter f = Filter.and(Filter.eq("id", role.getId()),
                                          filter);
                    ops.add(f);
                    int count = context.countObjects(Bundle.class, ops);
                    passesFilter = (count > 0);
                }

                if (passesFilter) {
                    if (container == null)
                        filtered = true;
                    else
                        filtered = isInherited(role, container);
                }
            }
        }
        
        return filtered;
    }

    /**
     * Return true if "superRole" is inherited directly or indirectly
     * by "subRole".
     */
    static private boolean isInherited(Bundle subRole, Bundle superRole) {
        
        boolean inherited = false;

        if (subRole != null && superRole != null) {
            List<Bundle> supers = subRole.getInheritance();
            if (supers != null) {
                inherited = supers.contains(superRole);
                if (!inherited) {
                    // not directly on the inheritance list, recurse up
                    for (Bundle sup : supers) {
                        inherited = isInherited(sup, superRole);
                        if (inherited)
                            break;
                    }
                }
            }
        }

        return inherited;
    }

    /**
     * Return true if a given role has a type that matches the
     * synchronization style.
     */
    static public boolean isRoleTypeRelevant(IntegrationConfig integration, 
                                             Bundle role) {

        boolean relevant = false;

        RoleTypeDefinition type = role.getRoleTypeDefinition();

        if (integration.isRoleSyncStyleDetectable()) {

            relevant = (type == null || type.isDetectable());
        }
        else if (integration.isRoleSyncStyleAssignable()) {

            relevant = (type == null || type.isAssignable());
        }
        else if (integration.isRoleSyncStyleDual()) {

            // either assignable or detectable
            relevant = (type == null || 
                        type.isDetectable() || type.isAssignable());
        }

        return relevant;
    }

    /**
     * Return true if a given role is managed by an IDM integration.
     */
    static public boolean isManagedRole(SailPointContext context,
                                        IntegrationConfig config, 
                                        Bundle role)
        throws GeneralException {

        return  (isFilteredRole(context, config, role) && 
                 isRoleTypeRelevant(config, role));
    }

    /**
     * For an IIQ identity, derive the corresponding native account name
     * for the corresponding user in a provisioning system.
     *
     * !! This needs more discussion, will we be consistently managing
     * a Link for these?
     */
    static public String getIntegrationIdentity(IntegrationConfig config,
                                                Identity identity)
        throws GeneralException {

        String id = null;

        Application app = config.getApplication();
        if (app == null) {
            // I guess this is okay, it means that the Identity names
            // are in sync, or maybe we should have an extended attribute
            // where the name would be stored if not using links?
        }
        else {
            // instances are irrelevant for IDM systems ?
            Link link = identity.getLink(app);
            if (link != null) 
                id = link.getNativeIdentity();
            else
                log.warn("Missing link for application: " + app.getName());
        }

        // fall back to the Identity if we couldn't find the link
        if (id == null && identity != null)
            id = identity.getName();

        return id;
    }

}
