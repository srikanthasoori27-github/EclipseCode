/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.authorization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import sailpoint.object.Bundle;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

public class RoleDetailHierarchyAuthorizer implements Authorizer {
    private String roleId;
    private String subRoleId;

    public RoleDetailHierarchyAuthorizer(String roleId, String subRoleId) {
        this.roleId = roleId;
        this.subRoleId = subRoleId;
    }

    @Override
    public void authorize(UserContext userContext) throws GeneralException {
        Bundle parent = userContext.getContext().getObjectById(Bundle.class, roleId);
        if (parent == null) {
            throw new ObjectNotFoundException(Bundle.class, roleId);
        }

        if (Util.isNullOrEmpty(subRoleId)) {
            throw new InvalidParameterException("subRoleId");
        }
        Bundle subRole = userContext.getContext().getObjectById(Bundle.class, subRoleId);
        if (subRole == null) {
            throw new ObjectNotFoundException(Bundle.class, subRoleId);
        }
        ArrayList<Bundle> examinedRoles = new ArrayList<Bundle>();
        if (!roleInHierarchy(parent, subRole, examinedRoles)) {
            throw new UnauthorizedAccessException();
        };
    }

    /**
     * Checks if the sub role is in the hierarchy, requirements, or permits of the parent role
     * @param parent
     * @param subRole
     * @param examined
     * @return
     * @throws GeneralException
     */
    private boolean roleInHierarchy (Bundle parent, Bundle subRole, List<Bundle> examined) throws GeneralException {
        if (examined == null) {
            examined = new ArrayList<Bundle>();
        }
        examined.add(parent);
        Collection<Bundle> permits = parent.getFlattenedPermits();
        Collection<Bundle> requirements = parent.getFlattenedRequirements();
        Collection<Bundle> inheritance = parent.getFlattenedInheritance();
        if (inheritance.contains(subRole) ||
                requirements.contains(subRole) ||
                permits.contains(subRole)) {
            return true;
        }

        //Deeply nested hierarchies aren't fully represented in the flattened lists so lets iterate keep trying
        //if we haven't found any matches yet

        for (Bundle req : Util.safeIterable(requirements)) {
            if (!examined.contains(req)) {
                if (roleInHierarchy(req, subRole, examined)) {
                    return true;
                }
            }
        }

        for (Bundle permit : Util.safeIterable(permits)) {
            if (!examined.contains(permit)) {
                if (roleInHierarchy(permit, subRole, examined)) {
                    return true;
                }
            }
        }

        for (Bundle inherit : Util.safeIterable(inheritance)){
            if (!examined.contains(inherit)) {
                if (roleInHierarchy(inherit, subRole, examined)) {
                    return true;
                }
            }
        }
        return false;
    }
}
